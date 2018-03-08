define([], function() {
    'use strict';

    return withDocumentCopyText;

    function withDocumentCopyText() {

        var copiedDocumentText,
            copiedDocumentTextStorageKey = 'SESSION_copiedDocumentText';

        this.after('initialize', function() {
            this.on('copydocumenttext', this.onDocumentTextCopy);
            Object.defineProperty(visalloData, 'copiedDocumentText', {
                get: () => this.getCopiedDocumentText()
            });
        });

        this.onDocumentTextCopy = function(event, data) {
            copiedDocumentText = data;
            if (data && data.snippet) {
                this.trigger('displayInformation', {
                    message: i18n('element.clipboard.reference.copied'),
                    subtitle: i18n('element.clipboard.reference.copied.subtitle'),
                    dismissDuration: 3000
                });
            }
            if ('localStorage' in window) {
                try {
                    localStorage.setItem(copiedDocumentTextStorageKey, JSON.stringify(data));
                } catch(e) {
                    console.error('Unable to set localStorage item');
                }
            }
        };

        this.getCopiedDocumentText = function() {
            var text;
            if ('localStorage' in window) {
                text = localStorage.getItem(copiedDocumentTextStorageKey);
                if (text) {
                    text = JSON.parse(text);
                }
            }

            if (text === null || _.isUndefined(text)) {
                text = copiedDocumentText;
            }

            return text;
        }
    }
});
