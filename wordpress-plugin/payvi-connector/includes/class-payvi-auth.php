<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Shared auth helpers for the PAYVI REST API.
 *
 * Auth model mirrors WooCommerce's own REST API: a key/secret pair sent as
 * HTTP Basic Auth (key = username, secret = password). This is a
 * well-understood, battle-tested pattern rather than a custom signature
 * scheme, which keeps the surface for bugs small.
 */
class Payvi_Auth {

	/**
	 * Checks the request's Basic Auth credentials against the stored
	 * key/secret. Returns true on success, or a WP_Error on failure.
	 *
	 * @param WP_REST_Request $request
	 * @return true|WP_Error
	 */
	public static function check( $request ) {
		$stored_key    = get_option( 'payvi_api_key' );
		$stored_secret = get_option( 'payvi_api_secret' );

		if ( empty( $stored_key ) || empty( $stored_secret ) ) {
			return new WP_Error(
				'payvi_not_configured',
				__( 'PAYVI is not configured yet. Open PAYVI Connector in WP Admin first.', 'payvi-connector' ),
				array( 'status' => 500 )
			);
		}

		list( $sent_key, $sent_secret ) = self::extract_credentials( $request );

		if ( '' === $sent_key || '' === $sent_secret ) {
			return new WP_Error(
				'payvi_unauthorized',
				__( 'Missing PAYVI credentials.', 'payvi-connector' ),
				array( 'status' => 401 )
			);
		}

		if ( ! hash_equals( (string) $stored_key, (string) $sent_key ) ||
			! hash_equals( (string) $stored_secret, (string) $sent_secret ) ) {
			return new WP_Error(
				'payvi_unauthorized',
				__( 'Invalid PAYVI credentials.', 'payvi-connector' ),
				array( 'status' => 401 )
			);
		}

		update_option( 'payvi_last_seen', time() );

		return true;
	}

	/**
	 * Reads key/secret from either the standard Authorization: Basic header
	 * or, as a fallback for hosts that strip the Authorization header
	 * (common on shared hosting / some proxies), from X-PAYVI-Key and
	 * X-PAYVI-Secret headers.
	 *
	 * @return array{0:string,1:string}
	 */
	private static function extract_credentials( $request ) {
		$php_auth_user = isset( $_SERVER['PHP_AUTH_USER'] ) ? sanitize_text_field( wp_unslash( $_SERVER['PHP_AUTH_USER'] ) ) : '';
		$php_auth_pw   = isset( $_SERVER['PHP_AUTH_PW'] ) ? sanitize_text_field( wp_unslash( $_SERVER['PHP_AUTH_PW'] ) ) : '';

		if ( '' !== $php_auth_user && '' !== $php_auth_pw ) {
			return array( $php_auth_user, $php_auth_pw );
		}

		$auth_header = $request->get_header( 'authorization' );
		if ( $auth_header && stripos( $auth_header, 'basic ' ) === 0 ) {
			$decoded = base64_decode( trim( substr( $auth_header, 6 ) ), true );
			if ( false !== $decoded && false !== strpos( $decoded, ':' ) ) {
				list( $user, $pass ) = explode( ':', $decoded, 2 );
				return array( sanitize_text_field( $user ), sanitize_text_field( $pass ) );
			}
		}

		$key    = $request->get_header( 'x-payvi-key' );
		$secret = $request->get_header( 'x-payvi-secret' );

		return array(
			$key ? sanitize_text_field( $key ) : '',
			$secret ? sanitize_text_field( $secret ) : '',
		);
	}
}
