require(['configuration/plugins/registry', 'util/vertex/formatters'], function(registry, F) {

    const GEOSHAPE_MIMETYPES = [
        'application/vnd.geo+json',
        'application/vnd.google-earth.kml+xml'
    ];

    // element inspector
    registry.registerExtension('org.visallo.layout.component', {
        identifier: 'org.visallo.layout.body',
        applyTo: (model, { constraints }) => constraints.includes('width') && !constraints.includes('height') && isGeoShapeFile(model),
        children: [
            {
                componentPath: 'org/visallo/web/product/map/dist/geoShapePreview',
                className: 'org-visallo-map-geoshape-preview'
            },
            { componentPath: 'detail/properties/properties', className: 'org-visallo-properties', modelAttribute: 'data' },
            { componentPath: 'comments/comments', className: 'org.visallo-comments', modelAttribute: 'data' },
            { componentPath: 'detail/relationships/relationships', className: 'org-visallo-relationships', modelAttribute: 'data' }
        ]
    });

    // fullscreen
    registry.registerExtension('org.visallo.layout.component', {
        identifier: 'org.visallo.layout.body',
        applyTo: (model, { constraints }) => !constraints.length && isGeoShapeFile(model),
        children: [
            {
                componentPath: 'org/visallo/web/product/map/dist/geoShapePreview',
                className: 'org-visallo-map-geoshape-preview'
            },
            { ref: 'org.visallo.layout.body.split' }
        ]
    });

    function isGeoShapeFile(model) {
        if (F.vertex.displayType(model) === 'document') {
            const mimeType = F.vertex.prop(model, 'http://visallo.org#mimeType');
            if (GEOSHAPE_MIMETYPES.includes(mimeType)) {
                return true;
            }
        }

        return false
    }
});
