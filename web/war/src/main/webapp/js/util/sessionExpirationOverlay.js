define([
    'flight/lib/component',
    './sessionExpirationOverlayTpl.hbs'
], function(
    defineComponent,
    template) {

    return defineComponent(Overlay);

    function Overlay() {

        this.after('teardown', function() {
            if (this.overlay) {
                this.overlay.remove();
            }
        });

        this.after('initialize', function() {
            // Offline overrides session timeout overlay
            require(['util/offlineOverlay'], Offline => {
                if (!$(document).lookupComponent(Offline)) {
                    $(() => {
                        this.overlay = $(template()).appendTo(document.body);
                    });
                }
            });
        });
    }
});
