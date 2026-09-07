<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * REST API surface consumed by the PAYVI Android app.
 *
 * Every route lives under /wp-json/payvi/v1/... and requires the Basic Auth
 * key/secret pair issued from the PAYVI Connector admin screen.
 *
 * This class only *reads* existing order/vendor/screenshot meta that other
 * snippets on this site already write (_mv_payment_screenshot_url,
 * _mv_vendor_name, _mv_chosen_payment_method, _jds_flag, _jds_match). It
 * writes only its own, separately namespaced meta keys (_payvi_*), so it
 * cannot collide with or break any existing snippet.
 */
class Payvi_Api {

	const NAMESPACE_V1 = 'payvi/v1';

	const STATUSES = array( 'pending', 'completed', 'not_sure', 'not_received', 'duplicate' );

	private static $instance = null;

	public static function instance() {
		if ( null === self::$instance ) {
			self::$instance = new self();
		}
		return self::$instance;
	}

	private function __construct() {
		add_action( 'rest_api_init', array( $this, 'register_routes' ) );
	}

	public function register_routes() {
		register_rest_route(
			self::NAMESPACE_V1,
			'/verify',
			array(
				'methods'             => WP_REST_Server::READABLE,
				'callback'            => array( $this, 'handle_verify' ),
				'permission_callback' => array( $this, 'permission_check' ),
			)
		);

		register_rest_route(
			self::NAMESPACE_V1,
			'/orders',
			array(
				'methods'             => WP_REST_Server::READABLE,
				'callback'            => array( $this, 'handle_list_orders' ),
				'permission_callback' => array( $this, 'permission_check' ),
				'args'                => array(
					'since'    => array(
						'type'              => 'integer',
						'default'           => 0,
						'sanitize_callback' => 'absint',
					),
					'per_page' => array(
						'type'              => 'integer',
						'default'           => 50,
						'sanitize_callback' => 'absint',
					),
				),
			)
		);

		register_rest_route(
			self::NAMESPACE_V1,
			'/orders/(?P<id>\d+)',
			array(
				'methods'             => WP_REST_Server::READABLE,
				'callback'            => array( $this, 'handle_get_order' ),
				'permission_callback' => array( $this, 'permission_check' ),
			)
		);

		register_rest_route(
			self::NAMESPACE_V1,
			'/orders/(?P<id>\d+)/status',
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => array( $this, 'handle_set_status' ),
				'permission_callback' => array( $this, 'permission_check' ),
			)
		);

		register_rest_route(
			self::NAMESPACE_V1,
			'/orders/(?P<id>\d+)/screenshot',
			array(
				'methods'             => WP_REST_Server::READABLE,
				'callback'            => array( $this, 'handle_get_screenshot' ),
				'permission_callback' => array( $this, 'permission_check' ),
			)
		);
	}

	public function permission_check( $request ) {
		return Payvi_Auth::check( $request );
	}

	/* ---------------------------------------------------------------
	 * /verify
	 * ------------------------------------------------------------- */

	public function handle_verify( $request ) {
		return new WP_REST_Response(
			array(
				'success'      => true,
				'site_name'    => get_bloginfo( 'name' ),
				'site_url'     => home_url(),
				'currency'     => get_woocommerce_currency(),
				'plugin_version' => PAYVI_VERSION,
				'server_time'  => time(),
			),
			200
		);
	}

	/* ---------------------------------------------------------------
	 * /orders  (list, incremental via ?since=<unix timestamp>)
	 * ------------------------------------------------------------- */

	public function handle_list_orders( $request ) {
		$since    = (int) $request->get_param( 'since' );
		$per_page = (int) $request->get_param( 'per_page' );
		$per_page = $per_page > 0 ? min( $per_page, 100 ) : 50;

		$args = array(
			'limit'   => $per_page,
			'orderby' => 'date_modified',
			'order'   => 'ASC',
			'return'  => 'objects',
			'type'    => 'shop_order',
		);

		if ( $since > 0 ) {
			// Documented WooCommerce CRUD query syntax: an operator-prefixed
			// value on a date column. We still re-check the timestamp below
			// in PHP as a defensive double-check.
			$args['date_modified'] = '>' . $since;
		}

		$orders = wc_get_orders( $args );

		$data = array();
		foreach ( $orders as $order ) {
			if ( ! ( $order instanceof WC_Order ) ) {
				continue;
			}
			if ( $since > 0 ) {
				$modified = $order->get_date_modified();
				if ( $modified && $modified->getTimestamp() <= $since ) {
					continue; // Defensive re-check, see comment above.
				}
			}
			$data[] = $this->order_to_array( $order );
		}

		return new WP_REST_Response(
			array(
				'success'     => true,
				'server_time' => time(),
				'orders'      => $data,
			),
			200
		);
	}

	/* ---------------------------------------------------------------
	 * /orders/{id}
	 * ------------------------------------------------------------- */

	public function handle_get_order( $request ) {
		$order = wc_get_order( (int) $request->get_param( 'id' ) );
		if ( ! $order ) {
			return new WP_Error( 'payvi_not_found', __( 'Order not found.', 'payvi-connector' ), array( 'status' => 404 ) );
		}
		return new WP_REST_Response(
			array(
				'success' => true,
				'order'   => $this->order_to_array( $order ),
			),
			200
		);
	}

	/* ---------------------------------------------------------------
	 * /orders/{id}/screenshot - authenticated file proxy, used by the app
	 * as a fallback when the public uploads URL is blocked/hotlink-protected.
	 * ------------------------------------------------------------- */

	public function handle_get_screenshot( $request ) {
		$order = wc_get_order( (int) $request->get_param( 'id' ) );
		if ( ! $order ) {
			return new WP_Error( 'payvi_not_found', __( 'Order not found.', 'payvi-connector' ), array( 'status' => 404 ) );
		}

		$attachment_id = (int) $order->get_meta( '_mv_payment_screenshot', true );
		$path          = $attachment_id > 0 ? get_attached_file( $attachment_id ) : false;

		if ( ! $path || ! file_exists( $path ) ) {
			return new WP_Error( 'payvi_no_screenshot', __( 'No screenshot found for this order.', 'payvi-connector' ), array( 'status' => 404 ) );
		}

		$mime = get_post_mime_type( $attachment_id );
		if ( ! $mime ) {
			$mime = 'application/octet-stream';
		}

		// Stream the file directly rather than loading it fully into memory.
		status_header( 200 );
		nocache_headers();
		header( 'Content-Type: ' . $mime );
		header( 'Content-Length: ' . filesize( $path ) );
		header( 'Content-Disposition: inline; filename="' . basename( $path ) . '"' );

		$fh = fopen( $path, 'rb' );
		if ( $fh ) {
			fpassthru( $fh );
			fclose( $fh );
		}
		exit;
	}

	/* ---------------------------------------------------------------
	 * POST /orders/{id}/status - app reports the auto-detected result.
	 * ------------------------------------------------------------- */

	public function handle_set_status( $request ) {
		$order = wc_get_order( (int) $request->get_param( 'id' ) );
		if ( ! $order ) {
			return new WP_Error( 'payvi_not_found', __( 'Order not found.', 'payvi-connector' ), array( 'status' => 404 ) );
		}

		$body   = $request->get_json_params();
		$status = isset( $body['status'] ) ? sanitize_key( $body['status'] ) : '';

		if ( ! in_array( $status, self::STATUSES, true ) ) {
			return new WP_Error(
				'payvi_invalid_status',
				sprintf(
					/* translators: %s: comma separated list of valid statuses */
					__( 'Invalid status. Must be one of: %s', 'payvi-connector' ),
					implode( ', ', self::STATUSES )
				),
				array( 'status' => 400 )
			);
		}

		$previous_status = $order->get_meta( '_payvi_status', true );

		$matched_sms = isset( $body['matched_sms'] ) ? sanitize_textarea_field( (string) $body['matched_sms'] ) : '';
		$matched_sms = mb_substr( $matched_sms, 0, 500 );

		$matched_fields = array();
		if ( isset( $body['matched_fields'] ) && is_array( $body['matched_fields'] ) ) {
			foreach ( array( 'amount', 'txn_id', 'number', 'name', 'date' ) as $field ) {
				if ( isset( $body['matched_fields'][ $field ] ) ) {
					$matched_fields[ $field ] = sanitize_text_field( (string) $body['matched_fields'][ $field ] );
				}
			}
		}

		$order->update_meta_data( '_payvi_status', $status );
		$order->update_meta_data( '_payvi_matched_sms', $matched_sms );
		$order->update_meta_data( '_payvi_matched_fields', wp_json_encode( $matched_fields ) );
		$order->update_meta_data( '_payvi_checked_at', current_time( 'mysql' ) );
		$order->save();

		if ( $status !== $previous_status ) {
			$labels = array(
				'pending'      => 'Pending',
				'completed'    => 'Completed',
				'not_sure'     => 'Not Sure',
				'not_received' => 'Not Received',
				'duplicate'    => 'Duplicate Payment',
			);
			$order->add_order_note(
				sprintf(
					'PAYVI: automatic payment verification set status to "%s".',
					isset( $labels[ $status ] ) ? $labels[ $status ] : $status
				)
			);
		}

		return new WP_REST_Response(
			array(
				'success'  => true,
				'order_id' => $order->get_id(),
				'status'   => $status,
			),
			200
		);
	}

	/* ---------------------------------------------------------------
	 * Helpers
	 * ------------------------------------------------------------- */

	private function order_to_array( WC_Order $order ) {
		$screenshot_id  = (int) $order->get_meta( '_mv_payment_screenshot', true );
		$screenshot_url = (string) $order->get_meta( '_mv_payment_screenshot_url', true );

		$is_duplicate = 'dupe' === $order->get_meta( '_jds_flag', true );
		$match_id     = (int) $order->get_meta( '_jds_match', true );

		$billing_name = trim( $order->get_billing_first_name() . ' ' . $order->get_billing_last_name() );

		$payvi_status  = $order->get_meta( '_payvi_status', true );
		$payvi_status  = $payvi_status ? $payvi_status : 'pending';
		$checked_at    = $order->get_meta( '_payvi_checked_at', true );

		$date_created  = $order->get_date_created();
		$date_modified = $order->get_date_modified();

		return array(
			'id'                     => $order->get_id(),
			'number'                 => $order->get_order_number(),
			'status'                 => $order->get_status(),
			'date_created'           => $date_created ? $date_created->format( DATE_ATOM ) : null,
			'date_modified'          => $date_modified ? $date_modified->format( DATE_ATOM ) : null,
			'currency'               => $order->get_currency(),
			'total'                  => $order->get_total(),
			'billing_name'           => $billing_name,
			'billing_phone'          => $order->get_billing_phone(),
			'billing_email'          => $order->get_billing_email(),
			'vendor_name'            => (string) $order->get_meta( '_mv_vendor_name', true ),
			'payment_method'         => (string) $order->get_meta( '_mv_chosen_payment_method', true ),
			'has_screenshot'         => ( $screenshot_id > 0 || '' !== $screenshot_url ),
			'screenshot_url'         => $screenshot_url,
			'screenshot_id'          => $screenshot_id,
			'screenshot_proxy_url'   => rest_url( self::NAMESPACE_V1 . '/orders/' . $order->get_id() . '/screenshot' ),
			'is_duplicate'           => $is_duplicate,
			'duplicate_match_order_id' => $is_duplicate ? $match_id : 0,
			'payvi_status'           => $payvi_status,
			'payvi_checked_at'       => $checked_at ? $checked_at : null,
		);
	}
}
