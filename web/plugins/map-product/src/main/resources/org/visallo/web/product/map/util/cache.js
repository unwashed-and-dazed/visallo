define(['openlayers', 'jscache'], function(ol, Cache) {

    const MAX_CACHE_ITEMS = 1000;
    const EXPIRATION_MINUTES = 5;
    const DEBUG = false;
    const SELECTED_COLOR = '#0088cc'

    const cache = new Cache(MAX_CACHE_ITEMS, DEBUG);
    const geoCache = new Cache(MAX_CACHE_ITEMS, DEBUG);
    const cacheOptions = {
        expirationSliding: EXPIRATION_MINUTES * 60
    }
    const FocusPadding = 3;
    const FocusCirclePadding = 6;
    const FocusRadius = 5;
    const FocusFill = new ol.style.Fill({ color: SELECTED_COLOR });
    const FocusStroke = new ol.style.Stroke({ color: '#ffffff', width: 1 });

    return {
        clear() {
            cache.clear();
            geoCache.clear();
        },

        getOrCreateGeometry(id, geoLocations) {
            const hash = ['geo', id, ...geoLocations.map(([latitude, longitude]) => latitude + ',' + longitude)].join(',')
            let geo = geoCache.getItem(hash);
            if (!geo) {
                geo = new ol.geom.MultiPoint(geoLocations.map(geo => ol.proj.fromLonLat(geo)))
                geoCache.setItem(hash, geo, cacheOptions);
            }
            return geo;
        },

        getOrCreateFeature(options, focused) {
            const key = hash(options, focused);
            const cached = cache.getItem(key)
            if (cached) {
                return cached;
            }

            const style = [new ol.style.Style({ image: new ol.style.Icon(options), zIndex: 1 })];
            if (focused) {
                const { imgSize, anchor = [0.5, 0.5] } = options;
                style.splice(0, 0, new ol.style.Style({
                    renderer(point, { context, feature, geometry, pixelRatio, resolution, rotation }) {
                        context.setTransform(1, 0, 0, 1, 0, 0);
                        const x = point[0] - imgSize[0] * anchor[0] - FocusPadding * pixelRatio;
                        const y = point[1] - imgSize[1] * anchor[1] - FocusPadding * pixelRatio;
                        const w = imgSize[0] + FocusPadding * 2 * pixelRatio;
                        const h = imgSize[1] + FocusPadding * 2 * pixelRatio;
                        const radius = FocusRadius * pixelRatio;

                        context.save();
                        context.globalAlpha = 0.2;
                        context.beginPath();
                        context.moveTo(x + radius, y);
                        context.lineTo(x + w - radius, y);
                        context.quadraticCurveTo(x + w, y, x + w, y + radius);
                        context.lineTo(x + w, y + h - radius);
                        context.quadraticCurveTo(x + w, y + h, x + w - radius, y + h);
                        context.lineTo(x + radius, y + h);
                        context.quadraticCurveTo(x, y + h, x, y + h - radius);
                        context.lineTo(x, y + radius);
                        context.quadraticCurveTo(x, y, x + radius, y);
                        context.closePath();
                        context.fillStyle = SELECTED_COLOR;
                        context.shadowBlur = radius / 2;
                        context.shadowColor = 'white';
                        context.shadowOffsetX = 0;
                        context.shadowOffsetY = 0;
                        context.fill();

                        context.restore();
                    },
                    zIndex: 0
                }))
            }
            cache.setItem(key, style, cacheOptions);

            return style;
        },

        getOrCreateCluster({
            count, radius: r, selected, selectionState, selectionCount,
            focusStats: { all, some, dim }
        }) {
            const key = [
                'cluster', count, r, selected,
                selectionState, selectionCount,
                all, some, dim
            ].join('|');

            let style = cache.getItem(key);
            if (style) {
                return style;
            }

            style = [
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: r * devicePixelRatio,
                        fill: new ol.style.Fill({ color: 'rgba(255,255,255,0.01)' })
                    })
                }),
                new ol.style.Style({
                    renderer([x, y], { context, pixelRatio }) {
                        const radius = r * pixelRatio;
                        var unselectedFill = 'rgba(241,59,60,0.8)',
                            selectedFill = 'rgba(0,112,195,0.8)',
                            selectedFillNoAlpha = 'rgb(0,112,195)',
                            unselectedStroke = '#AD2E2E',
                            stroke = selected ? '#08538B' : unselectedStroke,
                            lineWidth = 2 * pixelRatio,
                            textStroke = stroke,
                            fill = selected ? selectedFill : unselectedFill;

                        if (selected && selectionState === 'some') {
                            fill = unselectedFill;
                            textStroke = unselectedStroke;
                            stroke = unselectedStroke;
                        }

                        context.save();
                        if (dim && !some) {
                            context.globalAlpha = 0.4;
                        }
                        context.setTransform(1, 0, 0, 1, 0, 0);
                        context.translate(x, y);

                        if (some) {
                            context.beginPath();
                            context.arc(0, 0, radius + FocusCirclePadding * pixelRatio, 0, 2 * Math.PI, true);
                            context.fillStyle = 'rgba(0, 136, 204, 0.2)';
                            context.fill();
                            context.closePath();
                        }

                        context.beginPath();
                        context.arc(0, 0, radius, 0, 2 * Math.PI, true);
                        context.fillStyle = fill;
                        context.fill();

                        if (selectionState === 'some') {
                            context.strokeStyle = stroke;
                            context.lineWidth = Math.max(4 * pixelRatio, lineWidth);
                            context.stroke();
                            context.closePath();
                            const portion = Math.max(0.1, Math.min(0.9, selectionCount / count));
                            context.beginPath();
                            context.arc(0, 0, radius, Math.PI / -2, Math.PI * 2 * portion - Math.PI / 2, false);
                            context.strokeStyle = selectedFillNoAlpha;
                            context.stroke();
                            context.closePath();
                        } else {
                            context.strokeStyle = stroke;
                            context.lineWidth = lineWidth;
                            context.stroke();
                            context.closePath();
                        }

                        context.font = `bold condensed ${radius}px sans-serif`;
                        context.textAlign = 'center';
                        context.fillStyle = 'white';
                        context.textBaseline = 'middle';
                        context.strokeStyle = textStroke;
                        context.lineWidth = pixelRatio;

                        if (some && some !== count) {
                            const text = some.toString();
                            context.strokeText(text, 0, radius * -0.4);
                            context.fillText(text, 0, radius * -0.4);

                            context.font = `bold condensed ${radius * 0.7}px sans-serif`;
                            context.fillStyle = 'rgba(255,255,255,0.8)';
                            context.strokeText(count.toString(), 0, radius * 0.55);
                            context.fillText(count.toString(), 0, radius * 0.55);

                            context.beginPath();
                            context.moveTo(radius * -0.5, radius * 0.1);
                            context.lineTo(radius * 0.5, radius * 0.1);
                            context.strokeStyle = 'rgba(255,255,255,0.3)';
                            context.stroke();
                            context.closePath();

                        } else {
                            const text = count.toString();
                            context.strokeText(text, 0, 0);
                            context.fillText(text, 0, 0);
                        }

                        context.restore();
                    }
                })
            ];

            cache.setItem(key, style, cacheOptions);
            return style;
        },

        addFocus(radius, list) {
            const key = `focus${radius}`;
            let focusStyle = cache.getItem(key);
            if (!focusStyle) {
                focusStyle = new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: radius + 5,
                        fill: FocusFill,
                        stroke: FocusStroke
                    }),
                    zIndex: 0
                })
                focusStyle.getImage().setOpacity(0.2);
                cache.setItem(key, focusStyle, cacheOptions);
            }

            return [focusStyle, ...list];
        },

        addDim(radius, list) {
            const image = list.length && list[0].getImage();
            if (image) {
                image.setOpacity(0.4);
            }
            return list;
        },

        reset(radius, list) {
            const image = list.length && list[0].getImage();
            if (image && image.getOpacity() < 1) {
                image.setOpacity(1);
            }
            return list;
        }
    }

    function hash({ src, imgSize, scale, anchor }, focused) {
        return [focused, src, `${imgSize[0]},${imgSize[1]}`, scale, `${anchor[0]},${anchor[1]}`].join('|')
    }

});
