define([], function() {
    'use strict';

    function BetterGrid(options = {}) {
        const { spaceX = 300, spaceY = 100, ...rest } = options;
        this.spaceX = spaceX * devicePixelRatio;
        this.spaceY = spaceY * devicePixelRatio;
        this.options = rest;
    }

    BetterGrid.prototype.run = function() {
        var self = this,
            nodes = this.options.eles,
            bb = nodes.boundingBox(),
            len = nodes.length,
            x = bb.x1,
            y = bb.y1,
            linebreak = Math.round(
                    Math.sqrt(len * (this.spaceY / this.spaceX))
                ) * this.spaceX,
            getPos = function(node, i) {
                if ((x - bb.x1) > linebreak) {
                  x = bb.x1;
                  y += self.spaceY;
                }

                var position = { x: x, y: y };
                x += self.spaceX;
                return position;
            };

        this.options.eles.layoutPositions(this, this.options, getPos);

        return this;
    }

    return BetterGrid;
})
