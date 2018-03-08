define([
    'create-react-class',
    '../Attacher',
    '../RegistryInjectorHOC'
], function(createReactClass, Attacher, RegistryInjectorHOC) {
    'use strict';

    const DEFAULT_FLIGHT_VIEWER = 'util/visibility/default/view';

    const VisibilityViewer = createReactClass({
        render() {
            const { registry, style, value, ...rest } = this.props;
            const custom = _.first(registry['org.visallo.visibility']);

            // Use new react visibility renderer as default if no custom exists
            if (custom && custom.viewerComponentPath !== DEFAULT_FLIGHT_VIEWER) {
                return (
                    <Attacher
                        nodeClassName="visibility"
                        nodeStyle={style}
                        value={value}
                        componentPath={custom.viewerComponentPath}
                        {...rest} />
                );
            }

            return (
                <div className="visibility" style={style}>
                    {_.isUndefined(value) || value === '' ?
                        (<i>{i18n('visibility.blank')}</i>) :
                        value
                    }
                </div>
            )
        }
    });

    return RegistryInjectorHOC(VisibilityViewer, [
        'org.visallo.visibility'
    ]);
});
