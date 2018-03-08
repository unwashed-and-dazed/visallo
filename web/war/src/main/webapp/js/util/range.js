
define([
    'rangy-core',
    'rangy-text'
], function(rangy) {
    'use strict';

    if (!rangy.initialized) {
        console.warn('Rangy should have been initialized by ClipboardManager…')
        rangy.init();
    }

    var api = {

        clearSelection: function() {
            rangy.getSelection().removeAllRanges();
        },

        highlightOffsets: function(textElement, offsets) {
            const spans = textElement.querySelectorAll('span[data-info]');

            let found = null;
            for (var i = 0; i < spans.length; i++) {
                const info = JSON.parse(spans[i].dataset.info);
                if (info.start === offsets[0] && info.end === offsets[1]) {
                    found = spans[i]
                    break;
                }
            }

            if (!found) {
                console.warn('Unable to find term mention at offsets', offsets)
                return;
            }

            var newEl = found,
                $newEl = $(newEl),
                scrollParent = $newEl.scrollParent(),
                scrollTo = newEl.offsetTop;

            scrollParent.clearQueue().animate({
                scrollTop: scrollTo - 100
            }, {
                duration: 'fast',
                easing: 'easeInOutQuad',
                complete: function() {
                    $newEl.on(ANIMATION_END, function(e) {
                        $newEl.removeClass('fade-slow');
                    });
                    $newEl.addClass('fade-slow');
                }
            });
        },

        getNodesWithinRange: function(range) {
            var r = rangy.createRange();

            r.setStart(range.startContainer, range.startOffset);
            r.setEnd(range.endContainer, range.endOffset);

            return r.getNodes()
        },

        createSnippetFromNode: function(node, numberWords, limitToContainer) {
            var range = document.createRange();

            if (node.nodeType === 1) {
                node.normalize();
                range.selectNode(node);
            } else {
                throw new Error('node must be nodeType=1');
            }

            return api.createSnippetFromRange(range, numberWords, limitToContainer);
        },

        createSnippetFromRange: function(range, numberWords, limitToContainer) {
            var output = {},
                numberOfWords = numberWords || 4,
                text = range.toString(),
                contextRange = api.expandRangeByWords(range, numberOfWords, output, limitToContainer),
                context = contextRange.toString(),
                transform = function(str, prependEllipsis) {
                    if (str.match(/^[\s\n]*$/)) {
                        return '';
                    }

                    var words = $.trim(str).split(/\s+/);
                    if (words.length < numberOfWords) {
                        return str;
                    }

                    if (prependEllipsis) {
                        return '…' + str;
                    }

                    return str + '…';
                },
                contextHighlight = transform(output.before, true) +
                    '<span class="selection">' + text + '</span>' +
                    transform(output.after, false);

            return contextHighlight;
        },

        expandRangeByWords: function(range, numberWords, splitBeforeAfterOutput, limitToContainer) {

            var e = rangy.createRange(),
                i = 0,
                options = {
                    includeBlockContentTrailingSpace: false,
                    includePreLineTrailingSpace: false,
                    includeTrailingSpace: false
                },
                maxLoop = 1000;

            e.setStart(range.startContainer, range.startOffset);
            e.setEnd(range.endContainer, range.endOffset);

            // Move range start to include n more of words
            e.moveStart('word', -numberWords, options);
            if (limitToContainer) {
                i = 0;
                while (e.startContainer !== limitToContainer &&
                       $(e.startContainer).closest(limitToContainer).length === 0) {
                    if (++i > maxLoop) break;
                    e.moveStart('character', 1, options);
                }
                if (i) {
                    e.moveStart('character', -1, options);
                }
            }

            // Move range end to include n more words
            e.moveEnd('word', numberWords, options);

            if (limitToContainer) {
                i = 0;
                while (e.endContainer !== limitToContainer &&
                       $(e.endContainer).closest(limitToContainer).length === 0) {
                    if (++i > maxLoop) break;
                    e.moveEnd('character', -1, options);
                }
            }

            // Calculate what we just included and send that back
            if (splitBeforeAfterOutput) {
                var output = rangy.createRange();
                output.setStart(e.startContainer, e.startOffset);
                output.setEnd(range.startContainer, range.startOffset);
                splitBeforeAfterOutput.before = output.text();

                output.setStart(range.endContainer, range.endOffset);
                output.setEnd(e.endContainer, e.endOffset);
                splitBeforeAfterOutput.after = output.text();
            }

            return e;
        }
    };

    return api;
});
