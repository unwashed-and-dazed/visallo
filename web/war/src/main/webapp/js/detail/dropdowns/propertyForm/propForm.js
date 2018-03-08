define([
    'require',
    'flight/lib/component',
    'util/withDropdown',
    './propForm.hbs',
    'util/ontology/propertySelect',
    'tpl!util/alert',
    'util/withTeardown',
    'util/vertex/vertexSelect',
    'util/vertex/formatters',
    'util/withDataRequest'
], function(
    require,
    defineComponent,
    withDropdown,
    template,
    PropertySelector,
    alertTemplate,
    withTeardown,
    VertexSelector,
    F,
    withDataRequest
) {
    'use strict';

    return defineComponent(PropertyForm, withDropdown, withTeardown, withDataRequest);

    function PropertyForm() {

        this.defaultAttrs({
            propertyListSelector: '.property-list',
            saveButtonSelector: '.btn-primary',
            deleteButtonSelector: '.btn-danger',
            configurationSelector: '.configuration',
            configurationFieldSelector: '.configuration input',
            previousValuesSelector: '.previous-values',
            previousValuesDropdownSelector: '.previous-values-container .dropdown-menu',
            vertexContainerSelector: '.vertex-select-container',
            visibilitySelector: '.visibility',
            justificationSelector: '.justification',
            visibilityInputSelector: '.visibility input',
            allowDeleteProperty: true,
            allowEditProperty: true
        });

        this.before('initialize', function(n, c) {
            c.manualOpen = true;
        });

        this.after('initialize', function() {
            var self = this,
                property = this.attr.property,
                vertex = this.attr.data;

            this.justification = {};
            this.modified = {};

            this.on('click', {
                saveButtonSelector: this.onSave,
                deleteButtonSelector: this.onDelete,
                previousValuesSelector: this.onPreviousValuesButtons
            });
            this.on('keyup keydown', {
                configurationFieldSelector: this.onKeyup,
                justificationSelector: this.onKeyup,
                visibilityInputSelector: this.onKeyup
            });

            this.on('propertyerror', this.onPropertyError);
            this.on('propertychange', this.onPropertyChange);
            this.on('propertyinvalid', this.onPropertyInvalid);
            this.on('propertyselected', this.onPropertySelected);
            this.on('visibilitychange', this.onVisibilityChange);
            this.on('justificationchange', this.onJustificationChange);
            this.on('paste', {
                configurationFieldSelector: _.debounce(this.onPaste.bind(this), 10)
            });
            this.on('click', {
                previousValuesDropdownSelector: this.onPreviousValuesDropdown
            });
            this.$node.html(template({
                property: property,
                vertex: vertex,
                loading: this.attr.loading
            }));

            this.select('saveButtonSelector').prop('disabled', true);
            this.select('deleteButtonSelector').hide();

            if (this.attr.property) {
                this.trigger('propertyselected', {
                    disablePreviousValuePrompt: true,
                    property: _.chain(property)
                        .pick('displayName key name value visibility metadata'.split(' '))
                        .extend({
                            title: property.name
                        })
                        .value()
                });
            } else if (!vertex) {
                this.on('vertexSelected', this.onVertexSelected);
                VertexSelector.attachTo(this.select('vertexContainerSelector'), {
                    value: '',
                    focus: true,
                    defaultText: i18n('vertex.field.placeholder')
                });
                this.manualOpen();
            } else {
                this.setupPropertySelectionField();
            }
        });

        this.setupPropertySelectionField = function() {
            const { label, conceptType } = this.attr.data;
            const filter = { addable: true };
            if (label) {
                filter.relationshipId = label
            } else if (conceptType) {
                filter.conceptId = conceptType;
            }

            const propertyNode = this.select('propertyListSelector').show();
            propertyNode.teardownAllComponents();

            propertyNode.one('rendered', () => {
                this.on('opened', () => {
                    propertyNode.find('input').focus()
                })
                _.defer(() => {
                    this.manualOpen();
                })
            });

            PropertySelector.attachTo(propertyNode, {
                filter: {
                    conceptId: this.attr.data.conceptType,
                    relationshipId: this.attr.data.label,
                    addable: true
                },
                placeholder: i18n('property.form.field.selection.placeholder')
            });
        };

        this.onVertexSelected = function(event, data) {
            event.stopPropagation();

            if (data.vertex) {
                this.attr.data = data.vertex;
                this.setupPropertySelectionField();
            } else {
                this.select('propertyListSelector').hide();
            }
            this.trigger('propFormVertexChanged', data);
        };

        this.after('teardown', function() {
            this.select('visibilitySelector').teardownAllComponents();
            this.select('vertexContainerSelector').teardownComponent(VertexSelector);

            if (this.$node.closest('.buttons').length === 0) {
                this.$node.closest('tr').remove();
            }
        });

        this.onPaste = function(event) {
            var self = this,
                value = $(event.target).val();

            _.defer(function() {
                self.trigger(
                    self.select('justificationSelector'),
                    'valuepasted',
                    { value: value }
                );
            });
        };

        this.onPreviousValuesButtons = function(event) {
            var self = this,
                dropdown = this.select('previousValuesDropdownSelector'),
                buttons = this.select('previousValuesSelector').find('.active').removeClass('active'),
                action = $(event.target).closest('button').addClass('active').data('action');

            event.stopPropagation();
            event.preventDefault();

            if (action === 'add') {
                dropdown.hide();
                this.trigger('propertyselected', {
                    fromPreviousValuePrompt: true,
                    property: _.omit(this.currentProperty, 'value', 'key')
                });
            } else if (this.previousValues.length > 1) {
                this.trigger('propertyselected', {
                    property: _.omit(this.currentProperty, 'value', 'key')
                });

                dropdown.html(
                        this.previousValues.map(function(p, i) {
                            var visibility = p.metadata && p.metadata['http://visallo.org#visibilityJson'];
                            return _.template(
                                '<li data-index="{i}">' +
                                    '<a href="#">{value}' +
                                        '<div data-visibility="{visibilityJson}" class="visibility"/>' +
                                    '</a>' +
                                '</li>')({
                                value: F.vertex.prop(self.attr.data, self.previousValuesPropertyName, p.key),
                                visibilityJson: JSON.stringify(visibility || {}),
                                i: i
                            });
                        }).join('')
                    ).show();

                require(['util/visibility/view'], function(Visibility) {
                    dropdown.find('.visibility').each(function() {
                        var value = $(this).data('visibility');
                        Visibility.attachTo(this, {
                            value: value && value.source
                        });
                    });
                });

            } else {
                dropdown.hide();
                this.trigger('propertyselected', {
                    fromPreviousValuePrompt: true,
                    property: $.extend({}, this.currentProperty, this.previousValues[0])
                });
            }
        };

        this.onPreviousValuesDropdown = function(event) {
            var li = $(event.target).closest('li'),
                index = li.data('index');

            this.$node.find('.previous-values .edit-previous').addClass('active');
            this.trigger('propertyselected', {
                fromPreviousValuePrompt: true,
                property: $.extend({}, this.currentProperty, this.previousValues[index])
            });
        };

        this.onPropertySelected = function(event, data) {
            var self = this,
                property = data.property,
                disablePreviousValuePrompt = data.disablePreviousValuePrompt,
                propertyName = property && property.title,
                config = self.select('configurationSelector'),
                visibility = self.select('visibilitySelector'),
                justification = self.select('justificationSelector');

            this.trigger('propFormPropertyChanged', data);

            if (!property) {
                config.hide();
                visibility.hide();
                justification.hide();
                return;
            } else {
                config.show();
            }

            this.currentProperty = property;
            this.$node.find('.errors').hide();

            config.teardownAllComponents();
            visibility.teardownAllComponents();
            justification.teardownAllComponents();

            var vertexProperty = property.title === 'http://visallo.org#visibilityJson' ?
                    _.first(F.vertex.props(this.attr.data, property.title)) :
                    !_.isUndefined(property.key) ?
                    _.first(F.vertex.props(this.attr.data, property.title, property.key)) :
                    undefined,
                previousValue = vertexProperty && vertexProperty.value,
                visibilityValue = vertexProperty &&
                    vertexProperty.metadata &&
                    vertexProperty.metadata['http://visallo.org#visibilityJson'],
                sandboxStatus = vertexProperty && vertexProperty.sandboxStatus,
                isExistingProperty = typeof vertexProperty !== 'undefined',
                isEditingVisibility = propertyName === 'http://visallo.org#visibilityJson' || (
                    vertexProperty && vertexProperty.streamingPropertyValue
                ),
                previousValues = disablePreviousValuePrompt !== true && F.vertex.props(this.attr.data, propertyName),
                previousValuesUniquedByKey = previousValues && _.unique(previousValues, _.property('key')),
                previousValuesUniquedByKeyUpdateable = _.where(previousValuesUniquedByKey, {updateable: true});


            this.currentValue = this.attr.attemptToCoerceValue || previousValue;
            if (this.currentValue && _.isObject(this.currentValue) && ('latitude' in this.currentValue)) {
                this.currentValue = 'point(' + this.currentValue.latitude + ',' + this.currentValue.longitude + ')';
            }

            if (visibilityValue) {
                visibilityValue = visibilityValue.source;
                this.visibilitySource = { value: visibilityValue, valid: true };
            }

            if (property.name === 'http://visallo.org#visibilityJson') {
                vertexProperty = property;
                isExistingProperty = true;
                previousValues = null;
                previousValuesUniquedByKey = null;
            }

            if (data.fromPreviousValuePrompt !== true && this.attr.allowEditProperty) {
                if (previousValuesUniquedByKeyUpdateable && previousValuesUniquedByKeyUpdateable.length) {
                    this.previousValues = previousValuesUniquedByKeyUpdateable;
                    this.previousValuesPropertyName = propertyName;
                    this.select('previousValuesSelector')
                        .show()
                        .find('.active').removeClass('active')
                        .addBack()
                        .find('.edit-previous span').text(previousValuesUniquedByKeyUpdateable.length)
                        .addBack()
                        .find('.edit-previous small').toggle(previousValuesUniquedByKeyUpdateable.length > 1);

                    this.select('justificationSelector').hide();
                    this.select('visibilitySelector').hide();
                    this.select('previousValuesDropdownSelector').hide();

                    return;
                } else {
                    this.select('previousValuesSelector').hide();
                }
            }

            this.select('previousValuesDropdownSelector').hide();
            this.select('justificationSelector').show();
            this.select('visibilitySelector').show();

            var deleteButton = this.select('deleteButtonSelector')
                .toggle(
                    !!isExistingProperty &&
                    !isEditingVisibility &&
                    this.attr.allowDeleteProperty
                );

            var button = this.select('saveButtonSelector')
                .text(isExistingProperty ? i18n('property.form.button.update') : i18n('property.form.button.add'));

            button.prop('disabled', true);

            this.dataRequest('ontology', 'properties').done(function(properties) {
                var propertyDetails = properties.byTitle[propertyName];
                if (!propertyDetails.deleteable) {
                    deleteButton.hide();
                }
                self.currentPropertyDetails = propertyDetails;
                if (propertyName === 'http://visallo.org#visibilityJson') {
                    var val = vertexProperty && vertexProperty.value,
                        source = (val && val.source) || (val && val.value && val.value.source);
                    self.editVisibility(visibility, source);
                } else if (vertexProperty && vertexProperty.streamingPropertyValue && vertexProperty.metadata) {
                    var visibilityMetadata = vertexProperty.metadata['http://visallo.org#visibilityJson'];
                    self.editVisibility(visibility, visibilityMetadata.source);
                } else if (propertyDetails) {
                    var isCompoundField = propertyDetails.dependentPropertyIris &&
                        propertyDetails.dependentPropertyIris.length,
                        fieldComponent;

                    if (isCompoundField) {
                        const dependentProperties = property.key ?
                            F.vertex.props(self.attr.data, propertyName, property.key) :
                            F.vertex.props(self.attr.data, propertyName);
                        self.currentValue = propertyDetails.dependentPropertyIris.map((iri) => {
                            let property = dependentProperties.find((property) => property.name === iri);
                            return property === undefined ? '' : property.value;
                        });
                        fieldComponent = 'fields/compound/compound';
                    } else if (propertyDetails.displayType === 'duration') {
                        fieldComponent = 'fields/duration';
                    } else {
                        fieldComponent = propertyDetails.possibleValues ?
                            'fields/restrictValues' : 'fields/' + propertyDetails.dataType;
                    }

                    require([
                        fieldComponent,
                        'detail/dropdowns/propertyForm/justification',
                        'util/visibility/edit'
                    ], function(PropertyField, Justification, Visibility) {
                        if (self.attr.manualOpen) {
                            var $toHide = $()
                                .add(config)
                                .add(justification)
                                .add(visibility)
                                .hide();
                        }

                        Justification.attachTo(justification, {
                            justificationText: self.attr.justificationText,
                            sourceInfo: self.attr.sourceInfo
                        });

                        Visibility.attachTo(visibility, {
                            value: visibilityValue || ''
                        });

                        self.settingVisibility = false;
                        self.checkValid();
                        self.$node.find('configuration').hide();

                        self.on('fieldRendered', function() {
                            if ($toHide) {
                                $toHide.show();
                            }
                            self.manualOpen();
                        });
                        if (isCompoundField) {
                            PropertyField.attachTo(config, {
                                property: propertyDetails,
                                vertex: self.attr.data,
                                values: property.key !== undefined ?
                                    F.vertex.props(self.attr.data, propertyDetails.title, property.key) :
                                    null
                            });
                        } else {
                            PropertyField.attachTo(config, {
                                property: propertyDetails,
                                vertexProperty: vertexProperty,
                                value: self.attr.attemptToCoerceValue || previousValue,
                                tooltip: (!self.attr.sourceInfo && !self.attr.justificationText) ? {
                                    html: true,
                                    title:
                                        '<strong>' +
                                        i18n('justification.field.tooltip.title') +
                                        '</strong><br>' +
                                        i18n('justification.field.tooltip.subtitle'),
                                    placement: 'left',
                                    trigger: 'focus'
                                } : null
                            });
                        }
                        self.previousPropertyValue = self.getConfigurationValues();
                    });
                } else console.warn('Property ' + propertyName + ' not found in ontology');
            });
        };

        this.editVisibility = function(visibility, source) {
            var self = this;
            require(['util/visibility/edit'], function(Visibility) {
                Visibility.attachTo(visibility, {
                    value: source || ''
                });
                visibility.find('input').focus();
                self.settingVisibility = true;
                self.visibilitySource = { value: source || '', valid: true };

                self.checkValid();
                self.manualOpen();
            });
        }

        this.onVisibilityChange = function(event, data) {
            const { value, title, metadata } = this.currentProperty;
            const isVisibilityProp = title === 'http://visallo.org#visibilityJson';
            const isExistingProp = isVisibilityProp || (metadata && 'http://visallo.org#visibilityJson' in metadata);
            const isModified = (isExistingProp, isVisibilityProp) => {
                let current = data.value;
                let previous = isVisibilityProp ? value.source : metadata['http://visallo.org#visibilityJson'].source;

                if (isVisibilityProp && !isExistingProp) {
                    return !!current;
                } else {
                    return current ? current !== previous : !!previous;
                }
            };

            this.visibilitySource = data;
            this.modified.visibility = isExistingProp || isVisibilityProp
                ? isModified(isExistingProp, isVisibilityProp)
                : !!this.visibilitySource.value;

            this.select('visibilityInputSelector').toggleClass('invalid', !data.valid);
            this.checkValid();
        };

        this.onJustificationChange = function(event, data) {
            var self = this;

            this.justification = data;

            this.modified.justification = data.valid && (data.justificationText || data.sourceInfo);
            this.checkValid();
        };

        this.onPropertyInvalid = function(event, data) {
            event.stopPropagation();

            this.propertyInvalid = true;
            this.checkValid();
        };

        this.checkValid = function() {
            if (this.settingVisibility) {
                this.valid = this.visibilitySource && this.visibilitySource.valid;
            } else {
                var valid = !this.propertyInvalid &&
                    (this.visibilitySource && this.visibilitySource.valid) &&
                    (!_.isEmpty(this.justification) ? this.justification.valid : true);

                this.valid = valid;
            }

            if (this.valid && _.some(this.modified)) {
                this.select('saveButtonSelector').prop('disabled', false);
            } else {
                this.select('saveButtonSelector').prop('disabled', true);
            }
        };

        this.onPropertyChange = function(event, data) {
            var self = this;

            this.propertyInvalid = false;
            event.stopPropagation();

            var isCompoundField = this.currentPropertyDetails.dependentPropertyIris,
                transformValue = function(valueArray) {
                    if (valueArray.length === 1) {
                        if (_.isObject(valueArray[0]) && ('latitude' in valueArray[0])) {
                            return JSON.stringify(valueArray[0])
                        }
                        return valueArray[0];
                    } else if (valueArray.length === 2) {
                        // Must be geoLocation
                        return 'point(' + valueArray.join(',') + ')';
                    } else if (valueArray.length === 3) {
                        return JSON.stringify({
                            description: valueArray[0],
                            latitude: valueArray[1],
                            longitude: valueArray[2]
                        });
                    }
                };

            if (isCompoundField) {
                this.currentValue = _.map(data.values, transformValue);
            } else {
                this.currentValue = data.value;
            }

            this.currentMetadata = data.metadata;
            this.modified.value = this.currentProperty.value ? valueModified() : !!this.currentValue;
            this.checkValid();


            function valueModified() {
                var previousValue = self.previousPropertyValue,
                    propertyValue = self.getConfigurationValues();

                if (previousValue !== undefined) {
                    return propertyValue !== previousValue;
                } else {
                    return !!propertyValue;
                }
            }
        };

        this.onPropertyError = function(event, data) {
            var messages = this.markFieldErrors(data.error);

            this.$node.find('.errors').html(
                alertTemplate({
                    error: messages
                })
            ).show();

            _.defer(() => {
                this.clearLoading()
                this.saving = false;
            })
        };

        this.getConfigurationValues = function() {
            var config = this.select('configurationSelector').lookupAllComponents().shift();

            return _.isFunction(config.getValue) ? config.getValue() : config.getValues();
        };

        this.onKeyup = function(evt) {
            const valid = evt.which === $.ui.keyCode.ENTER &&
                $(evt.target).is('.configuration *,.visibility *,.justification *');

            if (evt.type === 'keydown') {
                this._keydownValid = valid;
            } else if (this._keydownValid && valid) {
                this._keydownValid = false;
                if (!this.saving) {
                    this.onSave();
                }
            }
        };

        this.onDelete = function() {
            _.defer(this.buttonLoading.bind(this, this.attr.deleteButtonSelector));
            this.trigger('deleteProperty', {
                vertexId: this.attr.data.id,
                property: _.pick(this.currentProperty, 'key', 'name'),
                node: this.node
            });
        };

        this.onSave = function(evt) {
            var self = this;

            if (!this.valid) return;

            this.saving = true;

            const vertexId = this.attr.data.id;
            const propertyKey = this.currentProperty.key;
            const propertyName = this.currentProperty.title;
            const oldMetadata = this.currentProperty.metadata;
            const { sourceInfo, justificationText } = this.justification;
            const justification = sourceInfo ? { sourceInfo } : justificationText ? { justificationText } : {};
            const oldVisibilitySource = oldMetadata && oldMetadata['http://visallo.org#visibilityJson']
                ? oldMetadata['http://visallo.org#visibilityJson'].source
                : undefined;
            const dependentPropertyIris = this.currentPropertyDetails.dependentPropertyIris;
            let value = this.currentValue;


            if (dependentPropertyIris) {
                value = value.reduce((valueMap, val, i) => {
                    if (!val && val !== false) {
                        val = null;
                    }

                    valueMap[dependentPropertyIris[i]] = val;
                    return valueMap;
                }, {})
            }

            _.defer(this.buttonLoading.bind(this, this.attr.saveButtonSelector));

            this.$node.find('input').tooltip('hide');

            this.$node.find('.errors').hide();
            if (propertyName.length &&
                (
                    this.settingVisibility ||
                    (
                        (_.isString(value) && value.length) ||
                        _.isNumber(value) ||
                        value
                    )
                )) {

                this.trigger('addProperty', {
                    isEdge: F.vertex.isEdge(this.attr.data),
                    vertexId: this.attr.data.id,
                    element: this.attr.data,
                    property: {
                        key: propertyKey,
                        name: propertyName,
                        value: value,
                        visibilitySource: this.visibilitySource.value,
                        oldVisibilitySource: oldVisibilitySource,
                        metadata: this.currentMetadata,
                        ...justification
                    },
                    node: this.node
                });
            }
        };
    }
});
