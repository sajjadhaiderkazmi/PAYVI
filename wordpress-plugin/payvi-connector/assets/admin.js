/* global PayviAdmin, qrcode */
(function () {
	'use strict';

	function renderQr( pairing ) {
		var holder = document.getElementById( 'payvi-qr-canvas' );
		if ( ! holder || typeof qrcode === 'undefined' ) {
			return;
		}

		try {
			var qr = qrcode( 0, 'M' ); // type 0 = auto-select smallest size, M = ~15% error correction
			qr.addData( JSON.stringify( pairing ) );
			qr.make();

			// Replace the <canvas> placeholder with the library's own tested
			// <img> tag (a self-contained data: URI) - avoids hand-written
			// pixel drawing code entirely.
			var img = document.createElement( 'div' );
			img.innerHTML = qr.createImgTag( 6, 4, 'PAYVI pairing QR code' );
			holder.parentNode.replaceChild( img.firstChild, holder );
			img.firstChild && ( img.firstChild.id = 'payvi-qr-canvas' );
		} catch ( e ) {
			var wrap = document.getElementById( 'payvi-qr-wrap' );
			if ( wrap ) {
				var err = document.createElement( 'p' );
				err.className = 'payvi-error';
				err.textContent = 'Could not render QR code. Use the manual key entry below instead.';
				wrap.insertBefore( err, wrap.firstChild );
			}
		}
	}

	function updateManualFields( pairing ) {
		var siteEl = document.getElementById( 'payvi-field-site' );
		var keyEl = document.getElementById( 'payvi-field-key' );
		var secretEl = document.getElementById( 'payvi-field-secret' );
		if ( siteEl ) siteEl.textContent = pairing.site;
		if ( keyEl ) keyEl.textContent = pairing.key;
		if ( secretEl ) secretEl.textContent = pairing.secret;
	}

	document.addEventListener( 'DOMContentLoaded', function () {
		var revealBtn = document.getElementById( 'payvi-reveal-btn' );
		var qrWrap = document.getElementById( 'payvi-qr-wrap' );
		var regenBtn = document.getElementById( 'payvi-regenerate-btn' );
		var regenStatus = document.getElementById( 'payvi-regen-status' );

		var currentPairing = ( typeof PayviAdmin !== 'undefined' && PayviAdmin.pairing ) || null;

		if ( revealBtn && qrWrap ) {
			revealBtn.addEventListener( 'click', function () {
				var showing = qrWrap.style.display !== 'none';
				if ( showing ) {
					qrWrap.style.display = 'none';
					revealBtn.textContent = 'Show Pairing QR Code';
					return;
				}
				qrWrap.style.display = '';
				revealBtn.textContent = 'Hide Pairing QR Code';
				if ( currentPairing ) {
					renderQr( currentPairing );
				}
			} );
		}

		if ( regenBtn ) {
			regenBtn.addEventListener( 'click', function () {
				if ( typeof PayviAdmin === 'undefined' ) {
					return;
				}
				var confirmed = window.confirm(
					'This disconnects the currently paired app. You will need to re-pair (scan the new QR code) in the PAYVI app. Continue?'
				);
				if ( ! confirmed ) {
					return;
				}

				regenBtn.disabled = true;
				if ( regenStatus ) regenStatus.textContent = ' Regenerating…';

				var xhr = new XMLHttpRequest();
				xhr.open( 'POST', PayviAdmin.ajaxUrl, true );
				xhr.setRequestHeader( 'Content-Type', 'application/x-www-form-urlencoded' );
				xhr.onload = function () {
					regenBtn.disabled = false;
					try {
						var res = JSON.parse( xhr.responseText );
						if ( res && res.success && res.data && res.data.pairing ) {
							currentPairing = res.data.pairing;
							updateManualFields( currentPairing );
							if ( qrWrap.style.display !== 'none' ) {
								renderQr( currentPairing );
							}
							if ( regenStatus ) regenStatus.textContent = ' Done — pairing regenerated.';
						} else {
							if ( regenStatus ) regenStatus.textContent = ' Failed to regenerate. Reload the page and try again.';
						}
					} catch ( e ) {
						if ( regenStatus ) regenStatus.textContent = ' Failed to regenerate. Reload the page and try again.';
					}
				};
				xhr.onerror = function () {
					regenBtn.disabled = false;
					if ( regenStatus ) regenStatus.textContent = ' Network error. Try again.';
				};
				xhr.send(
					'action=payvi_regenerate_keys&nonce=' + encodeURIComponent( PayviAdmin.nonce )
				);
			} );
		}
	} );
})();
