define([
    'create-react-class',
    'prop-types',
    'classnames',
    'components/Attacher'
], function(
    createReactClass,
    PropTypes,
    classNames,
    Attacher) {
    'use strict';

    const PADDING = 10;

    const ProductToolbarItem = createReactClass({

        propTypes: {
            item: PropTypes.shape({
                identifier: PropTypes.string.isRequired,
                itemComponentPath: PropTypes.string,
                icon: PropTypes.string,
                label: PropTypes.string,
                props: PropTypes.object
            }),
            active: PropTypes.bool,
            onClick: PropTypes.func,
            onItemMouseEnter: PropTypes.func,
            onItemMouseLeave: PropTypes.func,
            rightOffset: PropTypes.number
        },

        render() {
            const { active, item, onItemMouseEnter, onItemMouseLeave } = this.props;
            const { props: itemProps, icon, label, buttonClass, identifier, itemComponentPath, placementHint } = item;

            return (
                <li
                    className={classNames('toolbar-item', { active })}
                    onClick={this.onItemClick}
                    ref={(ref) => { this.item = ref }}
                    onMouseEnter={(event) => { onItemMouseEnter(identifier) }}
                    onMouseLeave={(event) => { onItemMouseLeave(identifier) }}
                >
                  {itemComponentPath
                      ? placementHint && placementHint === 'popover'
                          ? this.renderPopoverItem()
                          : this.renderItem()
                      : this.renderButton()}
                </li>
            );
        },

        renderButton() {
            const { active, item } = this.props;
            const { props: itemProps, icon, label, buttonClass, identifier, itemComponentPath } = item;

            return (
                <div className={classNames('button', buttonClass)}>
                    { icon ?
                        <div className="item-icon" style={{backgroundImage: `url(${icon})`}}></div>
                    : null}
                    <span>{label}</span>
                </div>
            )
        },

        renderItem() {
            const { props: itemProps, identifier, itemComponentPath } = this.props.item;

            return (
                <Attacher
                    key={identifier}
                    componentPath={itemComponentPath}
                    {...itemProps}
                />
            )
        },

        renderPopoverItem() {
            const { active, item } = this.props;
            const { props: itemProps = {}, icon, label, buttonClass, identifier, itemComponentPath } = item;


            return (
                <div>
                    <div className={classNames('button', 'has-popover', buttonClass)}>
                        { icon ?
                            <div className="item-icon" style={{backgroundImage: `url(${icon})`}}></div>
                        : null}
                        <span>{label}</span>
                    </div>
                    <div
                        style={{display: (active ? 'block' : 'none')}}
                        className="item-container"
                        ref={(ref) => { this.popover = ref }}
                    >
                       {active ? <Attacher
                            key={identifier}
                            componentPath={itemComponentPath}
                            afterAttach={this.positionPopover}
                            {...itemProps}
                            onResize={this.positionPopover}
                       /> : null}
                    </div>
                    <div className="arrow top"></div>
                </div>
            )
        },

        onItemClick(event) {
            if (!$(event.target).closest('.item-container').length) {
                const { props: itemProps = {}, identifier } = this.props.item;
                if (_.isFunction(itemProps.handler)) {
                    itemProps.handler();
                } else {
                    this.props.onClick(identifier);
                }
            }
        },

        /**
         * Call `props.onResize` after your component with placementHint `popover` changes size to update the popover's position
         * @callback org.visallo.product.toolbar.item~onResize
         */
        positionPopover() {
            const rightOffset = this.props.rightOffset;
            const { left: itemLeft, width: itemWidth, right: itemRight } = this.item.getBoundingClientRect();
            const { left, right, width } = this.popover.getBoundingClientRect();
            const windowWidth = $(window).width();
            const maxLeft = windowWidth - width - PADDING - rightOffset;
            const currentOffset = $(this.popover).offset();
            const positionLeft = Math.min(itemLeft, maxLeft);

            $(this.arrow).offset({ top: $(this.arrow).offset.top, left: (itemLeft + (itemWidth / 2))});
            $(this.popover).offset({ top: currentOffset.top, left: Math.max(positionLeft, 40) }); //menubar width
        }
    });

    return ProductToolbarItem;
});
