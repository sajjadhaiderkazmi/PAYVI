<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * WP Admin screen: shows the pairing QR code / manual key so the PAYVI
 * Android app can be connected, plus basic connection status and a
 * "regenerate" (disconnect + rotate credentials) action.
 */
class Payvi_Admin {

	const MENU_SLUG    = 'payvi-connector';
	const NONCE_ACTION = 'payvi_admin_action';

	private static $instance = null;

	public static function instance() {
		if ( null === self::$instance ) {
			self::$instance = new self();
		}
		return self::$instance;
	}

	private function __construct() {
		add_action( 'admin_menu', array( $this, 'register_menu' ) );
		add_action( 'admin_enqueue_scripts', array( $this, 'enqueue_assets' ) );
		add_action( 'wp_ajax_payvi_regenerate_keys', array( $this, 'ajax_regenerate_keys' ) );
	}

	public function register_menu() {
		add_submenu_page(
			'woocommerce',
			__( 'PAYVI Connector', 'payvi-connector' ),
			__( 'PAYVI Connector', 'payvi-connector' ),
			'manage_woocommerce',
			self::MENU_SLUG,
			array( $this, 'render_page' )
		);
	}

	public function enqueue_assets( $hook ) {
		if ( false === strpos( (string) $hook, self::MENU_SLUG ) ) {
			return;
		}

		wp_enqueue_style( 'payvi-admin', PAYVI_PLUGIN_URL . 'assets/admin.css', array(), PAYVI_VERSION );
		wp_enqueue_script( 'payvi-qrcode', PAYVI_PLUGIN_URL . 'assets/qrcode.min.js', array(), PAYVI_VERSION, true );
		wp_enqueue_script( 'payvi-admin', PAYVI_PLUGIN_URL . 'assets/admin.js', array( 'payvi-qrcode' ), PAYVI_VERSION, true );

		wp_localize_script(
			'payvi-admin',
			'PayviAdmin',
			array(
				'ajaxUrl' => admin_url( 'admin-ajax.php' ),
				'nonce'   => wp_create_nonce( self::NONCE_ACTION ),
				'pairing' => $this->get_pairing_payload(),
			)
		);
	}

	private function get_pairing_payload() {
		return array(
			'site'   => home_url(),
			'key'    => get_option( 'payvi_api_key' ),
			'secret' => get_option( 'payvi_api_secret' ),
			'v'      => 1,
		);
	}

	public function ajax_regenerate_keys() {
		check_ajax_referer( self::NONCE_ACTION, 'nonce' );

		if ( ! current_user_can( 'manage_woocommerce' ) ) {
			wp_send_json_error( array( 'message' => __( 'Permission denied.', 'payvi-connector' ) ), 403 );
		}

		update_option( 'payvi_api_key', 'pk_' . wp_generate_password( 32, false, false ) );
		update_option( 'payvi_api_secret', 'sk_' . wp_generate_password( 48, false, false ) );
		update_option( 'payvi_last_seen', 0 );

		wp_send_json_success( array( 'pairing' => $this->get_pairing_payload() ) );
	}

	public function render_page() {
		if ( ! current_user_can( 'manage_woocommerce' ) ) {
			wp_die( esc_html__( 'You do not have permission to access this page.', 'payvi-connector' ) );
		}

		$last_seen    = (int) get_option( 'payvi_last_seen', 0 );
		$is_connected = $last_seen > 0 && ( time() - $last_seen ) < ( 15 * MINUTE_IN_SECONDS );
		$last_seen_text = $last_seen > 0
			? human_time_diff( $last_seen, time() ) . ' ' . __( 'ago', 'payvi-connector' )
			: __( 'never', 'payvi-connector' );

		$pairing = $this->get_pairing_payload();
		?>
		<div class="wrap payvi-wrap">
			<h1><?php esc_html_e( 'PAYVI Connector', 'payvi-connector' ); ?></h1>
			<p><?php esc_html_e( 'Pair the PAYVI Android app with this store to automatically verify manual payment screenshots against SMS receipts.', 'payvi-connector' ); ?></p>

			<div class="payvi-status <?php echo $is_connected ? 'payvi-status--ok' : 'payvi-status--off'; ?>">
				<strong>
					<?php echo $is_connected
						? esc_html__( 'Connected', 'payvi-connector' )
						: esc_html__( 'Not connected', 'payvi-connector' ); ?>
				</strong>
				<span>
					<?php
					printf(
						/* translators: %s: human readable time since last app contact */
						esc_html__( 'Last app activity: %s', 'payvi-connector' ),
						esc_html( $last_seen_text )
					);
					?>
				</span>
			</div>

			<div class="payvi-card">
				<h2><?php esc_html_e( '1. Pair the app', 'payvi-connector' ); ?></h2>
				<p><?php esc_html_e( 'In the PAYVI app, tap "Connect Store" and scan this QR code. It is only readable by someone with access to this admin page.', 'payvi-connector' ); ?></p>

				<button type="button" id="payvi-reveal-btn" class="button button-primary">
					<?php esc_html_e( 'Show Pairing QR Code', 'payvi-connector' ); ?>
				</button>

				<div id="payvi-qr-wrap" style="display:none;">
					<canvas id="payvi-qr-canvas"></canvas>
					<p class="description">
						<?php esc_html_e( 'This code contains a secret key. Do not share a screenshot of it publicly.', 'payvi-connector' ); ?>
					</p>

					<h3><?php esc_html_e( 'Or enter manually in the app', 'payvi-connector' ); ?></h3>
					<table class="form-table">
						<tr>
							<th><?php esc_html_e( 'Site URL', 'payvi-connector' ); ?></th>
							<td><code id="payvi-field-site"><?php echo esc_html( $pairing['site'] ); ?></code></td>
						</tr>
						<tr>
							<th><?php esc_html_e( 'API Key', 'payvi-connector' ); ?></th>
							<td><code id="payvi-field-key"><?php echo esc_html( $pairing['key'] ); ?></code></td>
						</tr>
						<tr>
							<th><?php esc_html_e( 'API Secret', 'payvi-connector' ); ?></th>
							<td><code id="payvi-field-secret"><?php echo esc_html( $pairing['secret'] ); ?></code></td>
						</tr>
					</table>
				</div>
			</div>

			<div class="payvi-card">
				<h2><?php esc_html_e( '2. Security', 'payvi-connector' ); ?></h2>
				<p><?php esc_html_e( 'If a phone with the app is lost or you suspect the key leaked, regenerate the pairing below. Any already-connected app will need to be re-paired.', 'payvi-connector' ); ?></p>
				<button type="button" id="payvi-regenerate-btn" class="button button-secondary">
					<?php esc_html_e( 'Regenerate Pairing (disconnect current app)', 'payvi-connector' ); ?>
				</button>
				<span id="payvi-regen-status"></span>
			</div>

			<div class="payvi-card">
				<h2><?php esc_html_e( 'How this works', 'payvi-connector' ); ?></h2>
				<ol>
					<li><?php esc_html_e( 'The app reads new/updated orders from this store, including the payment screenshot already attached to each order.', 'payvi-connector' ); ?></li>
					<li><?php esc_html_e( 'The app runs on-device OCR on the screenshot and compares it with SMS messages received on the phone number(s) you select in the app.', 'payvi-connector' ); ?></li>
					<li><?php esc_html_e( 'The app reports back a status per order (Completed / Not Sure / Not Received / Duplicate Payment), stored as order meta and visible as an order note.', 'payvi-connector' ); ?></li>
				</ol>
			</div>
		</div>
		<?php
	}
}
