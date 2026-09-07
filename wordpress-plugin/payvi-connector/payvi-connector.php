<?php
/**
 * Plugin Name: PAYVI Connector
 * Plugin URI: https://myjda.net/
 * Description: Connects this WooCommerce store to the PAYVI Android app. Exposes a secure, paired REST API that lets the app read new orders (with their payment screenshots) and report back an automated payment verification status. Does not modify checkout, order storage, or any existing snippet — it only reads existing order data.
 * Version: 1.0.0
 * Author: PAYVI
 * Text Domain: payvi-connector
 * Requires PHP: 7.4
 * Requires Plugins: woocommerce
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit; // No direct access.
}

define( 'PAYVI_VERSION', '1.0.0' );
define( 'PAYVI_PLUGIN_FILE', __FILE__ );
define( 'PAYVI_PLUGIN_DIR', plugin_dir_path( __FILE__ ) );
define( 'PAYVI_PLUGIN_URL', plugin_dir_url( __FILE__ ) );

/**
 * Bail out early (with an admin notice) if WooCommerce isn't active.
 * This plugin only ever reads WooCommerce order data - it never assumes
 * WooCommerce is missing without telling the site admin why it's inactive.
 */
function payvi_woocommerce_missing_notice() {
	echo '<div class="notice notice-error"><p>';
	echo esc_html__( 'PAYVI Connector requires WooCommerce to be installed and active.', 'payvi-connector' );
	echo '</p></div>';
}

function payvi_is_woocommerce_active() {
	return class_exists( 'WooCommerce' );
}

require_once PAYVI_PLUGIN_DIR . 'includes/class-payvi-auth.php';
require_once PAYVI_PLUGIN_DIR . 'includes/class-payvi-api.php';
require_once PAYVI_PLUGIN_DIR . 'includes/class-payvi-admin.php';

/**
 * Activation: generate the pairing key/secret if they don't already exist.
 * Never overwrites an existing pair on activation (so deactivate/reactivate
 * does not silently disconnect an already-paired app).
 */
function payvi_activate() {
	if ( ! get_option( 'payvi_api_key' ) ) {
		update_option( 'payvi_api_key', 'pk_' . wp_generate_password( 32, false, false ) );
	}
	if ( ! get_option( 'payvi_api_secret' ) ) {
		update_option( 'payvi_api_secret', 'sk_' . wp_generate_password( 48, false, false ) );
	}
	if ( false === get_option( 'payvi_last_seen' ) ) {
		add_option( 'payvi_last_seen', 0 );
	}
	// Make sure our rewrite rules for the REST namespace are picked up.
	flush_rewrite_rules();
}
register_activation_hook( PAYVI_PLUGIN_FILE, 'payvi_activate' );

function payvi_deactivate() {
	flush_rewrite_rules();
}
register_deactivation_hook( PAYVI_PLUGIN_FILE, 'payvi_deactivate' );

/**
 * Bootstrap.
 */
function payvi_init() {
	if ( ! payvi_is_woocommerce_active() ) {
		add_action( 'admin_notices', 'payvi_woocommerce_missing_notice' );
		return;
	}

	Payvi_Api::instance();
	Payvi_Admin::instance();
}
add_action( 'plugins_loaded', 'payvi_init' );
