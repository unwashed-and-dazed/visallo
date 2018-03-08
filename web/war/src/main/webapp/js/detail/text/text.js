define([
    'flight/lib/component',
    'util/vertex/formatters',
    'configuration/plugins/registry',
    'util/css-stylesheet',
    'util/withDataRequest',
    'util/privileges',
    'util/jquery.withinScrollable',
    'util/withCollapsibleSections',
    'util/popovers/propertyInfo/withPropertyInfo',
    'util/popovers/withElementScrollingPositionUpdates',
    'util/dnd',
    'util/service/propertiesPromise',
    'colorjs',
    './transcriptEntries.hbs',
    'tpl!util/alert',
    'require',
    'sf'
], function(
    defineComponent,
    F,
    registry,
    stylesheet,
    withDataRequest,
    Privileges,
    jqueryWithinScrollable,
    withCollapsibleSections,
    withPropertyInfo,
    withElementScrollingPositionUpdates,
    dnd,
    config,
    colorjs,
    transcriptEntriesTemplate,
    alertTemplate,
    require,
    sf) {
    'use strict';

    const STYLE_STATES = { NORMAL: 0, HOVER: 1 };
    const CONFIG_MAX_SELECTION_PARAGRAPHS =
        config['detail.text.popover.maxSelectionParagraphs'] ?
        parseInt(config['detail.text.popover.maxSelectionParagraphs'], 10) : 5;
    const TEXT_PROPERTIES = [
        'http://visallo.org#videoTranscript',
        'http://visallo.org#text'
    ];
    const PREVIEW_SELECTORS = {
        audio: 'div .audio-preview',
        video: '.org-visallo-video'
    };

    const hasValidOffsets = data => {
        return _.isObject(data) &&
            _.isFinite(data.startOffset) &&
            _.isFinite(data.endOffset) &&
            data.startOffset >= 0 &&
            data.endOffset > data.startOffset;
    }

    var rangeUtils, d3, textStylesheet;

    /**
     * Replaces the content of a collapsible text section in the inspector.
     *
     * Only one extension can replace a given text section, the first one will
     * win.
     *
     * @param {string} componentPath The component to render instead of the text
     * @param {org.visallo.detail.text~shouldReplaceTextSectionForVertex} shouldReplaceTextSectionForVertex Whether the component should be rendered instead of the default
     */
    registry.documentExtensionPoint('org.visallo.detail.text', 'Replace Extracted Text with custom component', function(e) {
        return _.isFunction(e.shouldReplaceTextSectionForVertex) && _.isString(e.componentPath);
    }, 'http://docs.visallo.org/extension-points/front-end/detailText')

    return defineComponent(
        Text,
        withDataRequest,
        withCollapsibleSections,
        withPropertyInfo,
        withElementScrollingPositionUpdates
    );

    function descriptionProperty(p) {
        var textDescription = 'http://visallo.org#textDescription';
        return p[textDescription] || p.metadata[textDescription] || p.key || p.name;
    }

    function textPropertySort(p) {
        if (p.key === '') {
            return '1' + descriptionProperty(p);
        }
        return '0' + descriptionProperty(p);
    }

    function textPropertyId(p) {
        return p.name + (p.key || '')
    }

    function Text() {

        this.attributes({
            termsSelector: '.jref,.resolved,.resolvable',
            resolvedSelector: '.resolved',
            textSelector: '.text',
            avLinkSelector: '.av-link',
            detailSectionContainerSelector: '.org-visallo-layout-body',
            model: null
        });

        this.after('teardown', function() {
            if (this.scrollNode) {
                this.scrollNode.off('scrollstop scroll');
            }
            if (this.textExtension) {
                this.textExtension.teardown();
            }
            this.$node.off('mouseleave', this.attr.termsSelector);
        });

        this.after('initialize', function() {
            var self = this;
            if (textStylesheet) {
                textStylesheet.remove();
            }
            textStylesheet = stylesheet.addSheet();


            this.loadRule = _.memoize(this.loadRule.bind(this), function(color, selector, state) {
                return color + '|' + selector + '|' + state;
            });

            this.dataRequest('ontology', 'concepts').then(concepts => {
                this.concepts = concepts;
            })
            this.on(document, 'ontologyUpdated', this.updateEntityAndArtifactDraggables);

            this.on('mousedown mouseup click dblclick contextmenu', this.trackMouse);
            this.on(document, 'keyup', this.handleKeyup);

            this.updateEntityAndArtifactDraggablesNoDelay = this.updateEntityAndArtifactDraggables;
            this.updateEntityAndArtifactDraggables = _.throttle(this.updateEntityAndArtifactDraggables.bind(this), 250);

            this.around('onToggleCollapsibleSection', function(fn, event) {
                var args = _.rest(arguments, 1),
                    $section = $(event.target).closest('.text-section'),
                    key = $section.attr('data-key'),
                    propertyName = $section.attr('data-name');

                event.stopPropagation();
                if ($section.hasClass('expanded') || !$section.find('.text').is(':empty')) {
                    fn.apply(this, args);
                } else {
                    return this.openText(key, propertyName);
                }
            });

            this.model = this.attr.model;
            this.on('updateModel', function(event, data) {
                this.model = data.model;
                this.updateText();
            });
            this.on('click', {
                termsSelector: this.onTermClick,
                avLinkSelector: this.onAVLinkClick
            });
            this.on('focusOnSnippet', this.onFocusOnSnippet);
            this.on('editProperty', this.onEditProperty);
            this.on('hoverTerm', this.onHoverTerm);
            this.on('copy cut', {
                textSelector: this.onCopyText
            });
            this.on('dropdownClosed', this.onDropdownClosed);
            this.on(document, 'textUpdated', this.onTextUpdated);

            this.on('mouseover', { termsSelector: this.onHoverOver });
            this.$node.on('mouseleave', this.attr.termsSelector, this.onHoverLeave.bind(this));

            this.scrollNode = self.$node.scrollParent()
                .css('position', 'relative')
                .on('scrollstop', self.updateEntityAndArtifactDraggables)
                .on('scroll', self.updateEntityAndArtifactDraggables);
            this.updateText();
            this.updateEntityAndArtifactDraggables();
        });

        this.onEditProperty = function(evt, data) {
            var self = this,
                root = $('<div class="underneath">'),
                section = $(evt.target).closest('.text-section'),
                text = section.find('.text'),
                property = data && data.property;

            evt.stopPropagation();

            Promise.all([
                Promise.require('detail/dropdowns/propertyForm/propForm'),
                // Wait for expansion
                section.hasClass('expanded') ? Promise.resolve() : self.onToggleCollapsibleSection(evt)
            ]).spread(function(PropertyForm) {
                if (text.length) {
                    root.prependTo(text);
                }

                PropertyForm.teardownAll();
                PropertyForm.attachTo(root, {
                    data: self.model,
                    property: property
                });
            });
        };

        this.onFocusOnSnippet = function(event, data) {
            var self = this;
            Promise.resolve(this.updatingPromise)
                .then(function() {
                    return self.openText(data.textPropertyKey, data.textPropertyName)
                })
                .then(function() {
                    var $text = self.$node.find('.ts-' +
                            F.className.to(data.textPropertyKey + data.textPropertyName) + ' .text'),
                        $transcript = $text.find('.av-times'),
                        focusOffsets = data.offsets;

                    if ($transcript.length) {
                        var start = F.number.offsetValues(focusOffsets[0]),
                            end = F.number.offsetValues(focusOffsets[1]),
                            $container = $transcript.find('dd').eq(start.index);

                        rangeUtils.highlightOffsets($container.get(0), [start.offset, end.offset]);
                    } else {
                        rangeUtils.highlightOffsets($text.get(0), focusOffsets);
                    }
                })
                .done();
        };

        this.onCopyText = function(event) {
            var selection = getSelection(),
                target = event.target;

            if (!selection.isCollapsed && selection.rangeCount === 1) {

                var data = this.transformSelection(selection);
                if (hasValidOffsets(data)) {
                    this.trigger('copydocumenttext', data);
                }
            }
        };

        this.onTextUpdated = function(event, data) {
            if (data.vertexId === this.attr.model.id) {
                this.updateText();
            }
        };

        this.formatTimeOffset = function(time) {
            return sf('{0:h:mm:ss}', new sf.TimeSpan(time));
        };

        this.trackMouse = function(event) {
            var $target = $(event.target);

            if ($target.is('.resolved,.resolvable')) {
                if (event.type === 'mousedown') {
                    rangeUtils.clearSelection();
                }
            }

            if (event.type === 'contextmenu') {
                event.preventDefault();
            }

            if (~'mouseup click dblclick contextmenu'.split(' ').indexOf(event.type)) {
                this.mouseDown = false;
            } else {
                this.mouseDown = true;
            }

            if (isTextSelectable(event) && (event.type === 'mouseup' || event.type === 'dblclick')) {
                this.handleSelectionChange();
            }
        };

        this.handleKeyup = function(event) {
            if (event.shiftKey && isTextSelectable(event)) {
                this.handleSelectionChange();
            }
        };

        this.updateText = function() {
            var self = this;

            this.updatingPromise = Promise.resolve(this.internalUpdateText())
                .then(function() {
                    self.updateEntityAndArtifactDraggables();
                })
                .catch(function(e) {
                    console.error(e);
                    throw e;
                })

            return this.updatingPromise;
        }

        this.internalUpdateText = function internalUpdateText(_d3, _rangeUtils) {
            var self = this;

            if (!d3 && _d3) d3 = _d3;
            if (!rangeUtils && _rangeUtils) rangeUtils = _rangeUtils;

            if (!d3) {
                return Promise.all([
                    Promise.require('d3'),
                    Promise.require('util/range')
                ]).then(function(results) {
                    return internalUpdateText.apply(self, results);
                })
            }

            return this.dataRequest('ontology', 'properties')
                .then(function(properties) {
                    var self = this,
                        scrollParent = this.$node.scrollParent(),
                        scrollTop = scrollParent.scrollTop(),
                        expandedKey = this.$node.find('.text-section.expanded').data('key'),
                        expandedName = this.$node.find('.text-section.expanded').data('name'),
                        textProperties = _.filter(this.model.properties, function(p) {
                            var ontologyProperty = properties.byTitle[p.name];
                            if (!ontologyProperty) {
                                return false;
                            }

                            // support legacy ontologies where text is not set to longText
                            var isTextProperty = _.some(TEXT_PROPERTIES, function(name) {
                                return name === p.name;
                            });
                            if (isTextProperty) {
                                return true;
                            }

                            if (!ontologyProperty.userVisible) {
                                return false;
                            }
                            return ontologyProperty.displayType === 'longText';
                        });

                    d3.select(self.node)
                        .selectAll('div.highlightWrap')
                        .data([1])
                        .call(function() {
                            this.enter().append('div');
                            this.attr('class', 'highlightWrap highlight-underline');
                        })
                        .selectAll('section.text-section')
                        .data(_.sortBy(textProperties, textPropertySort), textPropertyId)
                        .call(function() {
                            this.enter()
                                .append('section')
                                .attr('class', 'text-section collapsible')
                                .call(function() {
                                    this.append('h1').attr('class', 'collapsible-header')
                                        .call(function() {
                                            this.append('strong');
                                            this.append('button').attr('class', 'info');
                                        });
                                    this.append('div').attr('class', 'text visallo-allow-dblclick-selection');
                                });

                            this.order();

                            this.attr('data-key', function(p) {
                                    return p.key;
                                })
                                .attr('data-name', function(p) {
                                    return p.name;
                                })
                                .each(function() {
                                    var p = d3.select(this).datum();
                                    $(this).removePrefixedClasses('ts-').addClass('ts-' + F.className.to(p.key + p.name));
                                });
                            this.select('h1 strong').text(descriptionProperty);
                            this.select('button.info').on('click', function(d) {
                                d3.event.stopPropagation();
                                self.showPropertyInfo(this, self.model, d);
                            });

                            this.exit().remove();
                        });

                    if (textProperties.length) {
                        if (this.attr.focus) {
                            return this.openText(this.attr.focus.textPropertyKey, this.attr.focus.textPropertyName)
                                .then(function() {
                                    var $text = self.$node.find('.ts-' +
                                            F.className.to(self.attr.focus.textPropertyKey + self.attr.focus.textPropertyName) + ' .text'),
                                        $transcript = $text.find('.av-times'),
                                        focusOffsets = self.attr.focus.offsets;

                                    if ($transcript.length) {
                                        var start = F.number.offsetValues(focusOffsets[0]),
                                            end = F.number.offsetValues(focusOffsets[1]),
                                            $container = $transcript.find('dd').eq(start.index);

                                        rangeUtils.highlightOffsets($container.get(0), [start.offset, end.offset]);
                                    } else {
                                        rangeUtils.highlightOffsets($text.get(0), focusOffsets);
                                    }
                                    self.attr.focus = null;
                                });
                        } else if ((expandedName && expandedKey) || textProperties.length === 1) {
                            return this.openText(
                                expandedKey || textProperties[0].key,
                                expandedName || textProperties[0].name,
                                {
                                    scrollToSection: textProperties.length !== 1
                                }
                            ).then(function() {
                                scrollParent.scrollTop(scrollTop);
                            });
                        } else if (textProperties.length > 1) {
                            return this.openText(textProperties[0].key, textProperties[0].name, {
                                expand: false
                            });
                        }
                    }
                }.bind(this));
        };

        this.openText = function(propertyKey, propertyName, options) {
            var self = this,
                expand = !options || options.expand !== false,
                $section = this.$node.find('.ts-' + F.className.to(propertyKey + propertyName)),
                isExpanded = $section.is('.expanded'),
                $info = $section.find('button.info'),
                selection = getSelection(),
                range = selection.rangeCount && selection.getRangeAt(0),
                hasSelection = isExpanded && range && !range.collapsed,
                hasOpenForm = isExpanded && ($section.find('.underneath').length || $('.detail-text-terms-popover').length);

            if (hasSelection || hasOpenForm) {
                this.reloadText = this.openText.bind(this, propertyKey, propertyName, options);
                return Promise.resolve();
            }

            $section.closest('.texts').find('.loading').removeClass('loading');
            if (expand && !isExpanded) {
                $info.addClass('loading');
            }

            if (this.openTextRequest) {
                this.openTextRequest.cancel();
                this.openTextRequest = null;
            }

            var extensions = _.filter(registry.extensionsForPoint('org.visallo.detail.text'), function(e) {
                    /**
                     * @callback org.visallo.detail.text~shouldReplaceTextSectionForVertex
                     * @param {object} model The vertex/edge
                     * @param {string} propertyName
                     * @param {string} propertyKey
                     */
                    return e.shouldReplaceTextSectionForVertex(self.model, propertyName, propertyKey);
                }),
                textPromise;

            if (extensions.length > 1) {
                console.warn('Multiple extensions wanting to override text', extensions);
            }

            if (extensions.length) {
                textPromise = Promise.require('util/component/attacher')
                    .then(function(Attacher) {
                        /**
                         * @typedef org.visallo.detail.text~Component
                         * @property {object} model The vertex/edge
                         * @property {string} propertyName
                         * @property {string} propertyKey
                         */
                        self.textExtension = Attacher()
                            .node($section.find('.text'))
                            .path(extensions[0].componentPath)
                            .params({
                                vertex: self.model,
                                model: self.model,
                                propertyName: propertyName,
                                propertyKey: propertyKey
                            })
                        return self.textExtension.attach();
                    })
            } else {
                this.openTextRequest = this.dataRequest(
                    'vertex',
                    'highlighted-text',
                    this.model.id,
                    propertyKey,
                    propertyName
                );

                textPromise = this.openTextRequest
                    .catch(function() {
                        return '';
                    })
                    .then(function(artifactText) {
                        var html = self.processArtifactText(artifactText);
                        if (expand) {
                            $section.find('.text')[0].innerHTML = html;
                        }
                    });
            }

            return textPromise
                .then(function() {
                    $info.removeClass('loading');
                    if (expand) {
                        $section.addClass('expanded');

                        self.updateEntityAndArtifactDraggablesNoDelay();
                        if (!options || options.scrollToSection !== false) {
                            self.scrollToRevealSection($section);
                        }
                    } else {
                        $section.removeClass('expanded');
                    }
                })
        };

        this.processArtifactText = function(text) {
            var self = this,
                warningText = i18n('detail.text.none_available');

            // Looks like JSON ?
            if (/^\s*{/.test(text)) {
                var json;
                try {
                    json = JSON.parse(text);
                } catch(e) { /*eslint no-empty:0*/ }

                if (json && !_.isEmpty(json.entries)) {
                    return transcriptEntriesTemplate({
                        entries: _.map(json.entries, function(e) {
                            return {
                                millis: e.start,
                                time: (_.isUndefined(e.start) ? '' : self.formatTimeOffset(e.start)) +
                                        ' - ' +
                                      (_.isUndefined(e.end) ? '' : self.formatTimeOffset(e.end)),
                                text: e.text
                            };
                        })
                    });
                } else if (json) {
                    text = null;
                    warningText = i18n('detail.transcript.none_available');
                }
            }

            return !text ? alertTemplate({ warning: warningText }) : text;
        };

        this.onDropdownClosed = function(event, data) {
            var self = this;
            _.defer(function() {
                self.disableSelection = false;
                self.checkIfReloadNeeded();
            })
        };

        this.checkIfReloadNeeded = function() {
            if (this.reloadText) {
                var func = this.reloadText;
                this.reloadText = null;
                func();
            }
        };

        this.onHoverOver = function(event) {
            this.setHoverTarget($(event.target).closest('.text'), event.target);
        };

        this.onHoverLeave = function(event) {
            clearTimeout(this.hoverLeaveTimeout);
            this.hoverLeaveTimeout = setTimeout(() => {
                this.setHoverTarget($(event.target).closest('.text'));
            }, 16);
        };

        this.setHoverTarget = function($text, target, options = {}) {
            const { hoverOnlyTarget = false } = options;

            if (target) {
                clearTimeout(this.hoverLeaveTimeout);
            }

            const ref = target ? this.getElementRefId(target) : null;
            if (ref !== this.currentHoverTarget) {
                if (this.currentHoverTarget) {
                    $text.removeClass(this.currentHoverTarget)
                }
                if (ref) {
                    const refs = [ref];
                    if (target && hoverOnlyTarget !== true) {
                        let parent = target;
                        while (!parent.classList.contains('text')) {
                            const r = this.getElementRefId(parent);
                            const info = this.getElementInfoUsingRef(parent);
                            const type = info && info.conceptType;
                            const concept = type && this.concepts.byId[type];
                            const color = concept && concept.color || '#000000';
                            const selector = '.text.' + r + ' .' + r;

                            if (parent.classList.contains('res')) {
                                this.loadRule(color, selector, STYLE_STATES.HOVER);
                            } else if (parent.classList.contains('jref')) {
                                this.loadRule('#0088cc', selector, STYLE_STATES.HOVER);
                            }

                            refs.push(r);
                            parent = parent.parentNode;
                        }
                    }
                    this.currentHoverTarget = refs.join(' ');
                    $text.addClass(this.currentHoverTarget);
                } else {
                    this.currentHoverTarget = null;
                }
            }
        };

        this.getElementRefId = function(element) {
            return element.dataset.refId ? element.dataset.refId : element.dataset.ref;
        };

        this.getElementInfoUsingRef = function(element) {
            let info = element.dataset.info;
            let ref = element.dataset.ref;
            if (!info && ref) {
                const fullInfo = $(element).closest('.text').find(`.${ref}[data-ref-id]`)[0]
                if (fullInfo) {
                    info = fullInfo.dataset.info;
                } else {
                    console.warn("Text contains a data-ref that doesn't exist in document", element);
                }
            }
            let parsedInfo = info ? JSON.parse(info) : null;
            if (parsedInfo) {
                parsedInfo.refId = element.dataset.refId || ref;
            }
            return parsedInfo;
        }

        this.onTermClick = function(event) {
            var self = this,
                $target = $(event.target);

            if ($target.is('.underneath') || $target.parents('.underneath').length) {
                return;
            }
            var sel = window.getSelection();
            if (sel && sel.rangeCount === 1 && !sel.isCollapsed) {
                return;
            }

            const $textSection = $target.closest('.text-section');
            const clicked = $target.parentsUntil('.text', 'span').addBack();
            const terms = [];
            clicked.each(function() {
                const info = self.getElementInfoUsingRef(this);
                if (info) {
                    terms.push(info)
                } else {
                    console.warn('Mention does not contain a data-info attribute', this);
                }
            })

            this.popover({
                node: $target,
                terms,
                propertyKey: $textSection.data('key'),
                propertyName: $textSection.data('name')
            });
        };

        this.getOffsets = function(root, range) {
            var rangeRelativeToText = range.cloneRange();
            rangeRelativeToText.selectNodeContents(root);
            rangeRelativeToText.setEnd(range.startContainer, range.startOffset);
            var mentionStart = rangeRelativeToText.toString().length;
            var mentionEnd = mentionStart + range.toString().length
            return { mentionStart, mentionEnd };
        }

        this.handleSelectionChange = _.debounce(function() {
            var sel = window.getSelection(),
                text = sel && sel.rangeCount === 1 ? $.trim(sel.toString()) : '';

            if (this.disableSelection) {
                return;
            }
            if (text && text.length > 0) {
                var anchor = $(sel.anchorNode),
                    focus = $(sel.focusNode),
                    is = '.detail-pane .text',
                    $anchorText = anchor.is(is) ? anchor : anchor.parents(is),
                    $focusText = focus.is(is) ? focus : focus.parents(is),
                    textContainer = $anchorText[0] || $focusText[0];

                // Ignore outside content text
                if ($anchorText.length === 0 || $focusText.length === 0) {
                    this.checkIfReloadNeeded();
                    return;
                }

                // Ignore if too long of selection
                var maxParagraphs = _.compact(text.replace(/(\s*<br>\s*)+/g, '\n').split('\n'));
                if (maxParagraphs.length > CONFIG_MAX_SELECTION_PARAGRAPHS) {
                    return requireAndCleanupActionBar();
                }

                if (sel.rangeCount === 0) {
                    this.checkIfReloadNeeded();
                    return;
                }

                if (Privileges.missingEDIT) {
                    return;
                }

                // Don't show action bar if dropdown opened
                if (this.$node.find('.text.dropdown').length) {
                    return;
                }

                var range = sel.rangeCount && sel.getRangeAt(0);

                if (!range) {
                    return;
                }

                var self = this;
                var $text = $(textContainer),
                    $textSection = $text.closest('.text-section'),
                    $textOffset = $text.closest('.nav-with-background').offset();

                const anchorTo = { range: range.cloneRange() };
                const transformed = this.transformSelection(sel);
                const { snippet, startOffset: mentionStart, endOffset: mentionEnd } = transformed;
                const selection = {
                    sign: text,
                    mentionStart,
                    mentionEnd,
                    snippet
                };

                const nodesFound = rangeUtils.getNodesWithinRange(range);
                let termList = [];
                if (nodesFound) {
                    const terms = {};
                    nodesFound.forEach(node => {
                        if (node.nodeType === 3) {
                            node = node.parentNode;
                        }
                        const $node = $(node);
                        if ($node.hasClass('text')) return;
                        const self = this;
                        $(node).parentsUntil('.text', 'span').addBack().each(function() {
                            const term = self.getElementInfoUsingRef(this)
                            if (term) {
                                terms[term.id] = term;
                            }
                        })
                    })
                    termList = Object.values(terms)
                }

                this.popover({
                    node: textContainer,
                    anchorTo,
                    selection,
                    terms: termList,
                    propertyKey: $textSection.data('key'),
                    propertyName: $textSection.data('name')
                })
            } else {
                this.checkIfReloadNeeded();
            }
        }, 250);

        this.popover = function({ node, ...options }) {
            if (this.TextPopover && $(node).lookupComponent(this.TextPopover)) {
                const previousSelectionSoClickWontTeardown = this._termPopover &&
                    this._termPopover.selection &&
                    options.selection;

                if (!previousSelectionSoClickWontTeardown) {
                    return;
                }
            }

            require(['./popover/popover'], TextPopover => {
                this.TextPopover = TextPopover;
                if (this._termPopover) {
                    $(this._termPopover.node).teardownComponent(TextPopover);
                }

                if (options.selection) {
                    const { mentionStart, mentionEnd } = options.selection
                    const validOffsets = (mentionStart + mentionEnd) >= 0 && mentionStart < mentionEnd;
                    if (!validOffsets) {
                        return;
                    }
                }

                TextPopover.attachTo(node, {
                    keepInView: true,
                    preferredPosition: 'below',
                    artifactId: this.model.id,
                    ...options
                });

                this._termPopover = options;
                this._termPopover.node = node;
            })
        };

        this.onHoverTerm = function(event, data) {
            const $text = $(event.target).closest('.text');
            let el;
            if (data) {
                el = $text.find('.' + data.id).get(0)
            }

            this.setHoverTarget($text, el, { hoverOnlyTarget: true });
        };

        this.tearDownDropdowns = function() {
            this.$node.find('.underneath').teardownAllComponents();
            this.disableSelection = false;
        };

        this.transformSelection = function(selection) {
            var $anchor = $(selection.anchorNode),
                $focus = $(selection.focusNode),
                textContainer = $anchor.closest('.text')[0],
                transcriptTextContainer = $anchor.closest('dd')[0],
                isTranscript = $anchor.closest('.av-times').length,
                offsetsFunction = isTranscript ?
                    'offsetsForTranscript' :
                    'offsetsForText',
                range = selection.getRangeAt(0),
                rangeOffsets = this.getOffsets(isTranscript ? transcriptTextContainer : textContainer, range),
                offsets = this[offsetsFunction]([
                    {el: $anchor, offset: rangeOffsets.mentionStart },
                    {el: $focus, offset: rangeOffsets.mentionEnd }
                ], '.text', _.identity),
                contextHighlight = rangeUtils.createSnippetFromRange(
                    range, undefined, textContainer
                );

            return {
                startOffset: offsets && offsets[0],
                endOffset: offsets && offsets[1],
                snippet: contextHighlight,
                vertexId: this.model.id,
                textPropertyKey: $anchor.closest('.text-section').data('key'),
                textPropertyName: $anchor.closest('.text-section').data('name'),
                text: selection.toString(),
                vertexTitle: F.vertex.title(this.model)
            };
        };

        this.offsetsForText = function(input, parentSelector, offsetTransform) {
            return input.map(i => i.offset);
        };

        this.offsetsForTranscript = function(input) {
            var self = this,
                index = input[0].el.closest('dd').data('index'),
                endIndex = input[1].el.closest('dd').data('index');

            if (index !== endIndex) {
                return console.warn('Unable to select across timestamps');
            }

            var rawOffsets = this.offsetsForText(input, 'dd', function(offset) {
                    return F.number.offsetValues(offset).offset;
                }),
                bitMaskedOffset = _.map(rawOffsets, _.partial(F.number.compactOffsetValues, index));

            return bitMaskedOffset;
        };

        this.updateEntityAndArtifactDraggables = function() {
            var self = this,
                scrollNode = this.scrollNode,
                words = this.select('resolvedSelector'),
                validWords = $(words);

            if (!scrollNode) {
                scrollNode = this.scrollNode = this.$node.scrollParent();
            }

            // Filter list to those in visible scroll area
            if (scrollNode && scrollNode.length) {
                validWords = validWords.withinScrollable(scrollNode);
            }

            if (validWords.length === 0) {
                return;
            }

            var currentlyDragging = null;

            validWords
                .each(function() {
                    var info = self.getElementInfoUsingRef(this),
                        type = info && info.conceptType,
                        concept = type && self.concepts.byId[type];

                    if (concept) {
                        const classes = this.className.split(' ').filter(className => {
                            return !(className.indexOf('conceptId-') === 0 && className !== concept.className)
                        });

                        if (!(concept.className in classes)) {
                            classes.push(concept.className);
                            this.className = classes.join(' ');
                            self.loadSelectorForConcept(concept);
                        } else {
                            this.className = classes.join(' ');
                        }
                    }
                })

            if (Privileges.canEDIT) {

                words
                    .off('dragover drop dragenter dragleave')
                    .on('dragover', function(event) {
                        if (event.target.classList.contains('resolved')) {
                            event.preventDefault();
                        }
                    })
                    .on('dragenter dragleave', function(event) {
                        if (event.target.classList.contains('resolved')) {
                            $(event.target).toggleClass('drop-hover', event.type === 'dragenter');
                        }
                    })
                    .on('drop', function(event) {
                        event.preventDefault();
                        $(event.target).removeClass('drop-hover');

                        if (event.target.classList.contains('resolved')) {
                            const elements = dnd.getElementsFromDataTransfer(event.originalEvent.dataTransfer);

                            if (elements && elements.vertexIds.length === 1) {
                                const targetInfo = self.getElementInfoUsingRef(event.target)
                                if (targetInfo && targetInfo.resolvedToVertexId) {
                                    const $textSection = $(event.target).closest('.text-section');
                                    const sourceVertexId = elements.vertexIds[0];
                                    const targetVertexId = targetInfo.resolvedToVertexId;

                                    self.popover({
                                        node: event.target,
                                        sourceVertexId,
                                        targetVertexId,
                                        propertyKey: $textSection.data('key'),
                                        propertyName: $textSection.data('name')
                                    })
                                }
                            }

                        }
                    })
            }
        };

        this.loadSelectorForConcept = function(concept) {
            if (!concept.color) {
                return;
            }

            const className = concept.rawClassName || concept.className;
            if (!className) {
                return;
            }

            const conceptColor = colorjs(concept.color);
            if (conceptColor.red === 0 && conceptColor.green === 0 & conceptColor.blue === 0) {
                return;
            }

            this.loadRule(concept.color, '.highlight-underline .res.' + className, STYLE_STATES.NORMAL);
        };

        this.loadRule = function(color, selector, state) {
            require(['detail/text/highlight-styles/underline.hbs'], tpl => {
                const definition = function(state, template) {
                    return (template || tpl)({
                        ...(_.object(_.map(STYLE_STATES, (v, k) => [k.toLowerCase(), v === state]))),
                        colors: {
                            normal: colorjs(color).setAlpha(1.0),
                            background: colorjs(color).setAlpha(0.1)
                        }
                    });
                };
                textStylesheet.addRule(selector, definition(state));
            });
        };

        this.scrollToRevealSection = function($section) {
            var scrollIfWithinPixelsFromBottom = 150,
                y = $section.offset().top,
                sectionScrollY = $section.offset().top - $section.offsetParent().offset().top,
                scrollParent = $section.scrollParent(),
                scrollTop = scrollParent.scrollTop(),
                height = scrollParent.outerHeight(),
                fromBottom = height - y;
            sectionScrollY += scrollTop + scrollIfWithinPixelsFromBottom;
            if (fromBottom < scrollIfWithinPixelsFromBottom) {
                scrollParent.animate({
                    scrollTop: sectionScrollY - height
                }, 'fast');
            }
        };

        this.scrollToMediaPreview = function($detailBody) {
            if (!this.mediaType) {
                this.mediaType = $detailBody.find(PREVIEW_SELECTORS.audio).parent().length > 0 ? 'audio' : 'video';
                this.$mediaNode = this.mediaType === 'audio' ?
                    $detailBody.find(PREVIEW_SELECTORS.audio).parent() :
                    $detailBody.find(PREVIEW_SELECTORS.video);
            }
            var $scrollParent = visalloData.isFullscreen ? $('html, body') : $detailBody,
                scrollTop = visalloData.isFullscreen ? this.$mediaNode.offset().top : this.$mediaNode.position().top;

            $scrollParent.animate({
                scrollTop: scrollTop
            }, 'fast');
        };

        this.onAVLinkClick = function(event, data) {
            var seekTo = data.el.dataset.millis || '';
            var transcriptKey = $(event.target).parents('section').data().key;

            if (seekTo) {
                this.trigger(this.$node.parents('.type-content'), 'avLinkClicked', {
                    seekTo: seekTo,
                    autoPlay: false,
                    transcriptKey: transcriptKey
                });

                this.scrollToMediaPreview(this.$node.parents(this.attr.detailSectionContainerSelector));
            }
        };
    }

    function isTextSelectable(event) {
        return ($(event.target).closest('.opens-dropdown').length === 0 &&
            $(event.target).closest('.underneath').length === 0 &&
            !($(event.target).parent().hasClass('currentTranscript')) &&
            !($(event.target).hasClass('alert alert-error')));
    }

    function requireAndCleanupActionBar() {
        return Promise.require('util/actionbar/actionbar')
            .then(function(ActionBar) {
                ActionBar.teardownAll();
                return ActionBar;
            });
    }
});
