define(['util/vertex/formatters'], function(F) {

    const FALLBACK_PREFIX = 'Visallo_ElementIds: ';
    let supportsMultipleTypes;
    // IE doesn't support setData([mimetype], ...), it only supports setData('text', ...)
    const checkIfSupportsMultipleTypes = (dataTransfer) => {
        if (supportsMultipleTypes !== undefined) {
            return supportsMultipleTypes;
        } else {
            try {
                dataTransfer.setData('CHECK_ALLOWS_MANY_TYPES', 'true')
                const data = dataTransfer.getData('CHECK_ALLOWS_MANY_TYPES')

                if (data) {
                    supportsMultipleTypes = true;
                }
                return true;
            } catch(e) {
                //Firefox throws exception on setData() for a read-only dataTransfer (from a drop event)
                if (e.name === 'NoModificationAllowedError') {
                    return true;
                } else {
                    supportsMultipleTypes = false;
                    return false;
                }
            }
        }
    }

    return {
        dataTransferHasValidMimeType(dataTransfer, mimeTypes = []) {
            if (checkIfSupportsMultipleTypes(dataTransfer)) {
                return _.any(dataTransfer.types, type => mimeTypes.includes(type));
            } else {
                const text = dataTransfer.getData('Text');
                return text && text.startsWith(FALLBACK_PREFIX) && mimeTypes.includes(VISALLO_MIMETYPES.ELEMENTS);
            }
        },
        setDataTransferWithElements(dataTransfer, { vertexIds, edgeIds, elements = [] }) {
            const typeToData = segmentToTypes(vertexIds, edgeIds, elements);
            if (checkIfSupportsMultipleTypes(dataTransfer)) {
                _.each(typeToData, (data, type) => {
                    if (data) {
                        dataTransfer.setData(type, data);
                    }
                })
            } else {
                dataTransfer.setData('Text', FALLBACK_PREFIX + typeToData[VISALLO_MIMETYPES.ELEMENTS])
            }
            dataTransfer.effectAllowed = 'all';

            Promise.all([
                Promise.require('data/web-worker/store/element/actions'),
                visalloData.storePromise
            ]).spread((actions, store) => store.dispatch(actions.setFocus({ elementIds: [] })));
        },
        getElementsFromDataTransfer(dataTransfer) {
            var dataStr;
            if (checkIfSupportsMultipleTypes(dataTransfer)) {
                dataStr = dataTransfer.getData(VISALLO_MIMETYPES.ELEMENTS);
            } else {
                const text = dataTransfer.getData('Text');
                if (text && text.indexOf(FALLBACK_PREFIX) === 0) {
                    dataStr = text.substring(FALLBACK_PREFIX.length);
                }
            }

            if (dataStr) {
                return JSON.parse(dataStr);
            }
        }
    }

    function segmentToTypes(vertexIds = [], edgeIds = [], elements) {
        const hasFullElements = elements.length > 0;
        if (hasFullElements) {
            vertexIds = [];
            edgeIds = [];
            elements.forEach(({ id, type }) => {
                if (type === 'extendedDataRow') {
                    if (id.elementType === 'VERTEX') {
                        vertexIds.push(id.elementId);
                    } else {
                        edgeIds.push(id.elementId);
                    }
                } else if (type === 'vertex') {
                    vertexIds.push(id);
                } else {
                    edgeIds.push(id);
                }
            })
        }
        const url = F.vertexUrl.url(hasFullElements ? elements : vertexIds.concat(edgeIds), visalloData.currentWorkspaceId);
        const plain = hasFullElements ?
            elements.map(item => [
                F.vertex.title(item), F.vertexUrl.url([item], visalloData.currentWorkspaceId)
            ].join('\n')).join('\n\n') :
            url

        return {
            'text/uri-list': url,
            'text/plain': plain,
            'Text': plain,
            [VISALLO_MIMETYPES.ELEMENTS]: JSON.stringify({ vertexIds, edgeIds })
        }
    }
})
