define([
    'flight/lib/component',
    '../templates/columnEditor.hbs',
    './util',
    'util/ontology/conceptSelect',
    'util/withDataRequest',
    'util/vertex/formatters',
    'util/visibility/edit',
    'util/ontology/propertySelect',
    'require'
], function(
    defineComponent,
    template,
    util,
    ConceptSelect,
    withDataRequest,
    F,
    Visibility,
    FieldSelection,
    require) {
    'use strict';

    var idIncrementor = 0;

    const headerTypeToDataTypes = type => {
        if (type) {
            switch (type) {
                case 'Boolean':
                    return ['string', 'boolean'];

                case 'Number':
                    return ['geoLocation', 'integer', 'currency', 'decimal', 'double', 'number', 'string'];

                case 'Date':
                case 'DateTime':
                    return ['date'];

                case 'String':
                    return ['string'];
            }
        }
    };


    return defineComponent(ColumnEditor, withDataRequest);

    function ColumnEditor() {

        this.defaultAttrs({
            selectSelector: 'select.entity',
            addMappingButtonSelector: '.add-mapping',
            closeSelector: 'button.cancel-mapping',
            identSelector: '.identifier'
        });

        this.after('initialize', function() {
            var self = this;

            this.on('visibilitychange', this.onVisibilityChanged);
            this.on('conceptSelected', this.onConceptSelected);
            this.on('propertyselected', this.onPropertySelected);
            this.on('addAuxillaryData', this.onAddAuxillary);
            this.on('change', {
                selectSelector: this.onChangeEntity
            })
            this.on('click', {
                closeSelector: this.onClose,
                identSelector: this.onIdentifierClick,
                addMappingButtonSelector: this.onAdd
            })

            Promise.resolve(this.render())
                .then(function() {
                    self.trigger('fieldRendered');
                })
        });

        this.after('teardown', function() {
            this.$node.empty();
        })

        this.onIdentifierClick = function(event) {
            var checked = $(event.target).is(':checked');
            this.mapping.hints.isIdentifier = checked;
            event.stopPropagation();
        };

        this.render = function() {
            var self = this;

            this.$node.html(template(this.attr));

            if (this.attr.vertices.length === 0) {
                this.prepareForNewEntity();
            } else {
                var property,
                    previousMapping = _.find(this.attr.vertices, function(v) {
                    var prop = _.find(v.properties, function(p) {
                        if (p.key === self.attr.key) return true;
                        if (!p.hints) return false;
                        if ('columnLatitude' in p.hints) {
                            if (self.attr.key === p.hints.columnLatitude) {
                                p.key = self.attr.key;
                                return true;
                            }
                        }
                        if ('columnLongitude' in p.hints) {
                            if (self.attr.key === p.hints.columnLongitude) {
                                p.key = self.attr.key;
                                return true;
                            }
                        }
                    });
                    if (prop) {
                        property = prop;
                    }

                    return prop;
                })
                if (previousMapping) {
                    this.selectedVertex = previousMapping;
                } else {
                    this.selectedVertex = _.last(this.attr.vertices);
                }
                this.select('selectSelector').val(this.selectedVertex.id);
                this.$node.find('.identifier input').prop('checked',
                    property && property.hints && property.hints.isIdentifier
                );
                return this.showField(property)
            }
        };

        this.onClose = function() {
            this.trigger('updateMappedObject');
        };

        this.onAdd = function() {
            var key = this.attr.key;

            this.selectedVertex.properties = _.reject(this.selectedVertex.properties, function(p) {
                return p.key === key;
            });
            this.selectedVertex.properties.push(this.mapping);
            this.trigger('updateMappedObject', {
                type: 'vertex',
                finished: true,
                column: this.mapping.column,
                object: this.selectedVertex
            })
            this.teardown();
        };

        this.onChangeEntity = function(event) {
            var self = this,
                $select = $(event.target),
                selectedId = $select.val();

            this.cleanupUnusedEntities();

            this.$node.find('.field input').removeClass('invalid');
            this.$node.find('.field-error').hide()

            if (selectedId) {
                this.selectedVertex = _.findWhere(this.attr.vertices, { id: selectedId });
                this.$node.find('.concept,.entity-visibility').hide();
                this.$node.find('.aux_fields').teardownAllComponents().empty();

                var properties = util.findPropertiesForColumnInObject(
                    this.selectedVertex,
                    this.attr.key
                );
                this.showField(_.first(properties));
            } else {
                this.prepareForNewEntity();
            }

        };

        this.cleanupUnusedEntities = function() {
            var self = this,
                $select = this.select('selectSelector');

            $select.find('option').toArray().reverse().forEach(function(opt) {
                if (opt.value) {
                    if (_.every(self.attr.vertices, function(v) {
                        return v.id !== opt.value;
                    })) {
                        opt.parentNode.removeChild(opt);
                    }
                }
            });
        };

        this.prepareForNewEntity = function() {
            this.selectedVertex = null;
            ConceptSelect.attachTo(this.$node.find('.concept').teardownComponent(ConceptSelect).show(), {
                defaultText: i18n('csv.file_import.concept.select.placeholder')
            });

            const visibilityAttr = { placeholder: i18n('csv.file_import.entity.visibility.placeholder') };
            if (this.attr.defaultVisibilitySource) {
                visibilityAttr.value = this.entityVisibility || this.attr.defaultVisibilitySource;
            }

            Visibility.attachTo(this.$node.find('.entity-visibility').teardownComponent(Visibility).show(), visibilityAttr);

            this.$node.find('.aux_fields').teardownAllComponents().empty();
            this.$node.find('.field,.field-visibility,.identifier').hide();
        };

        this.onVisibilityChanged = function(event, data) {
            if ($(event.target).is('.entity-visibility')) {
                if (this.selectedVertex) {
                    this.selectedVertex.visibilitySource = data.value;
                }
                this.entityVisibility = data.value
            } else {
                this.mapping.visibilitySource = data.value;
            }
        };

        this.onConceptSelected = function(event, data) {
            var self = this;

            if (data.concept) {
                this.selectedVertex = {
                    id: 'vertex-' + (idIncrementor++),
                    visibilitySource: this.entityVisibility,
                    displayName: data.concept.displayName + ' #' + (this.attr.vertices.length + 1),
                    properties: [
                        {
                            name: util.CONCEPT_TYPE,
                            value: data.concept.id
                        }
                    ]
                };

                this.select('selectSelector')
                    .append(
                        $('<option>')
                            .val(this.selectedVertex.id)
                            .text(this.selectedVertex.displayName)
                    )
                    .val(this.selectedVertex.id)

                this.showField();
            } else {
                this.$node.find('.field').teardownComponent(FieldSelection).hide();
            }

            this.cleanupUnusedEntities();
        };

        this.showField = function(property) {
            var self = this,
                conceptProperty = _.findWhere(this.selectedVertex.properties, { name: util.CONCEPT_TYPE });
            if (conceptProperty) {
                return this.dataRequest('ontology', 'propertiesByConceptId', conceptProperty.value)
                    .then(function(properties) {
                        var ontologyProperty = property && _.findWhere(properties.list, { title: property.name });
                        var type = self.attr.type;
                        var onlyDataTypes = headerTypeToDataTypes(type);
                        FieldSelection.attachTo(self.$node.find('.field').teardownComponent(FieldSelection).show(), {
                            placeholder: i18n('csv.file_import.mapping.properties.placeholder'),
                            onlyDataTypes: onlyDataTypes,
                            rollupCompound: false,
                            selectedProperty: ontologyProperty,
                            limitParentConceptId: conceptProperty.value
                        });
                        if (ontologyProperty) {
                            return self.setOntologyProperty(ontologyProperty, property);
                        } else {
                            self.$node.find('.field-visibility').hide();
                        }
                    })
            }
        }

        this.onPropertySelected = function(event, data) {
            const selectedProperty = data && data.property;
            if (selectedProperty && this.selectedVertex.properties.some((property) => property.name === selectedProperty.title)) {
                this.$node.find('.field input').addClass('invalid');
                this.$node.find('.field-error').show()
                    .text(i18n('csv.file_import.mapping.error.duplicate.property', this.selectedVertex.displayName));
            } else {
                this.$node.find('.field input').removeClass('invalid');
                this.$node.find('.field-error').hide()
                this.setOntologyProperty(selectedProperty, this.mapping)
            }
        };

        this.setOntologyProperty = function(property, previousMapping) {
            var key = this.attr.key,
                dataType = property && property.dataType,
                mapping = property ? (previousMapping || {
                    name: property.title,
                    key: key
                }) : null;

            if (mapping && !_.isObject(mapping.hints)) {
                mapping.hints = {};
            }
            this.mapping = mapping;

            this.$node.find('.aux_fields').teardownAllComponents().empty();
            const hasMapping = Boolean(mapping);
            this.$node.find('.identifier').toggle(hasMapping);
            const $visibility = this.$node.find('.field-visibility').teardownComponent(Visibility).toggle(hasMapping);
            this.select('addMappingButtonSelector').prop('disabled', !hasMapping);

            if (mapping) {
                Visibility.attachTo($visibility, {
                    value: this.mapping.visibilitySource || this.attr.defaultVisibilitySource,
                    placeholder: i18n('csv.file_import.property.visibility.placeholder')
                });

                switch (dataType) {
                    case 'boolean':
                    case 'date':
                    case 'geoLocation':
                        return this.attachAuxiliaryField(property, mapping)

                    // Passthroughs
                    case 'currency':
                    case 'decimal':
                    case 'double':
                    case 'integer':
                    case 'number':
                    case 'string':
                        break;

                    default:
                        console.error('Data type', dataType, 'not supported');
                }
            }
        };

        this.onAddAuxillary = function(event, data) {
            this.mapping.hints = data;
        };

        this.attachAuxiliaryField = function(property, mapping) {
            var self = this;

            if (this.attr.type) {
                if (this.attr.type === 'Date') return;
                if (this.attr.type === 'Boolean') return;
            }

            return new Promise(function(fulfill, reject) {
                require(['./auxillary/' + property.dataType], function(AuxillaryField) {
                    var node = self.$node.find('.aux_fields');

                    node.removePrefixedClasses('type-').addClass('type-' + property.dataType)

                    AuxillaryField.attachTo(node, {
                        property: property,
                        mapping: mapping,
                        allHeaders: self.attr.allHeaders
                    })
                    fulfill();
                });
            })
        };

    }
});
