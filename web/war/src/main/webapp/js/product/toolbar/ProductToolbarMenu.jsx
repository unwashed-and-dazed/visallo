define([
    'create-react-class',
    'prop-types',
    'components/Attacher'
], function(createReactClass, PropTypes, Attacher) {
    'use strict';

    const ProductToolbarMenu = ({ active, identifier, items, onToggle, onItemMouseEnter, onItemMouseLeave }) => {
        if (_.isEmpty(items)) {
            return null;
        }

        return (
            <div className="toolbar-menu"
                onMouseEnter={() => { onItemMouseEnter(identifier) }}
                onMouseLeave={() => { onItemMouseLeave(identifier) }}
            >
                <button
                    className={active ? 'active' : null}
                    onClick={() => { onToggle(identifier) }}
                    title={i18n('product.toolbar.toggle')}>Item</button>
                <div style={{display: (active ? 'block' : 'none')}} className="item-container">
                    <ul>{
                        items.map(({ identifier, itemComponentPath, props }) => {
                            return <Attacher
                                nodeType="li"
                                key={identifier}
                                componentPath={itemComponentPath}
                                {...(props || {})} />
                        })
                    }</ul>
                </div>
            </div>
        );
    };

    ProductToolbarMenu.propTypes = {
        identifier: PropTypes.string.isRequired,
        active: PropTypes.bool,
        items: PropTypes.array,
        onToggle: PropTypes.func,
        onItemMouseEnter: PropTypes.func,
        onItemMouseLeave: PropTypes.func
    };

    return ProductToolbarMenu;
});
