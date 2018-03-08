define([], function() {
    'use strict';

    return function() {
        //IE fires blur event if clicking on a scrollbar which prevents click/drag to scroll the dropdown
        $.fn.typeahead.Constructor.prototype = {
            ...$.fn.typeahead.Constructor.prototype,
            listen: function () {
                this.$element
                    .on('focus', $.proxy(this.focus, this))
                    .on('blur', $.proxy(this.blur, this))
                    .on('keypress', $.proxy(this.keypress, this))
                    .on('keyup', $.proxy(this.keyup, this))

                if (this.eventSupported('keydown')) {
                    this.$element.on('keydown', $.proxy(this.keydown, this))
                }

                this.$menu
                    .on('click', $.proxy(this.click, this))
                    .on('mouseenter', 'li', $.proxy(this.mouseenter, this))
                    .on('mouseleave', 'li', $.proxy(this.mouseleave, this))
                    .on('mouseenter', $.proxy(this.mouseenterMenu, this))
                    .on('mouseleave', $.proxy(this.mouseleaveMenu, this))
            },

            blur: function (e) {
                const self = this;

                if (this.shown && this.mousedover) {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    _.defer(function() { self.$element.focus(); });
                } else {
                    this.focused = false
                    if (!this.mousedover && this.shown) this.hide()
                }
            },
            mouseenter: function (e) {
                this.$menu.find('.active').removeClass('active')
                $(e.currentTarget).addClass('active')
            },
            mouseleave: function (e) {
                this.$menu.find('.active').removeClass('active')
                $(e.currentTarget).addClass('active')
            },
            mouseenterMenu: function() {
                this.mousedover = true;
            },
            mouseleaveMenu: function() {
                this.mousedover = false;
            }
        }
    };
});
