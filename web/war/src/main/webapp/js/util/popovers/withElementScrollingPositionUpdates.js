
define([], function() {
    'use strict';

    return withElementScrollingPositionUpdates;

    function withElementScrollingPositionUpdates() {

        this.before('teardown', function() {
            this.removePositionUpdating();
        });

        this.after('initialize', function() {
            this.on('registerForPositionChanges', this.onRegisterForPositionChanges);
            this.on('unregisterForPositionChanges', this.onUnregisterForPositionChanges);
        });

        this.onRegisterForPositionChanges = function(event, data) {

            event.stopPropagation();

            let range = data.anchorTo && data.anchorTo.range;
            if (range) {
                range = range.cloneRange()
            }

            var self = this,
                $target = $(event.target),
                scroller = data && data.scrollSelector ?
                    $target.closest(data.scrollSelector) :
                    $target.scrollParent(),
                sendPositionChange = function() {
                    let position, width, height;
                    if (range) {
                        const rects = range.getClientRects();
                        let box;
                        if (rects.length) {
                            box = _.sortBy(rects, function(r) {
                                return r.top * -1;
                            })[0];
                        } else {
                            box = range.getBoundingClientRect();
                        }

                        width = box.width;
                        height = box.height;
                        const { left, top } = box;
                        position = { left, top };

                        if (scroller[0] === document) {
                            position.top += scroller.scrollTop();
                        }
                    } else {
                        width = $target.outerWidth();
                        height = $target.outerHeight();
                        position = $target.offset();
                        if ((width === 0 || height === 0) && _.isFunction(event.target.getBBox)) {
                            var box = event.target.getBBox();
                            width = box.width;
                            height = box.height;
                        }
                    }

                    const eventData = {
                        position: {
                            x: position.left + width / 2,
                            y: position.top + height / 2,
                            xMin: position.left,
                            xMax: position.left + width,
                            yMin: position.top,
                            yMax: position.top + height
                        }
                    };
                    if (data && data.anchorTo) {
                        eventData.anchor = data.anchorTo;
                    }
                    self.trigger(event.target, 'positionChanged', eventData);
                };

            this.positionChangeScroller = scroller;
            this.sendPositionChange = sendPositionChange;

            this.on(document, 'graphPaddingUpdated', sendPositionChange);
            scroller.on('scroll.positionchange', sendPositionChange);
            sendPositionChange();
        };

        this.onUnregisterForPositionChanges = function(event, data) {
            this.removePositionUpdating();
        };

        this.removePositionUpdating = function() {
            if (this.positionChangeScroller) {
                this.positionChangeScroller.off('.positionchange');
            }
            if (this.sendPositionChange) {
                this.off(document, 'graphPaddingUpdated', this.sendPositionChange);
            }
        }
    }
})
