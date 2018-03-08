
define([
    'flight/lib/component',
    'react',
    'react-dom',
    'util/vertex/formatters',
    'util/withDataRequest',
    'util/dnd',
    'require'
], function(
    defineComponent,
    React,
    ReactDOM,
    F,
    withDataRequest,
    dnd,
    require) {
    'use strict';

    var SHOW_CHANGES_TEXT_SECONDS = 3;
    var COMMENT_ENTRY_IRI = 'http://visallo.org/comment#entry';
    var DiffPanel;

    return defineComponent(Diff, withDataRequest);

    function titleForEdgesVertices(vertex, vertexId, diffsGroupedByElement) {
        if (vertex) {
            return F.vertex.title(vertex);
        }

        var matchingDiffs = diffsGroupedByElement[vertexId],
            diff = matchingDiffs && matchingDiffs.length && matchingDiffs[0];

        if (diff && diff.title) {
            return diff.title;
        }

        return null;
    }

    function Diff() {

        this.attributes({
            diffs: null,
            schemaTypeSelector: '.schema-type'
        })

        this.render = function() {
            if (!DiffPanel) {
                require(['./DiffPanel'], panel => {
                    DiffPanel = panel
                    this.render();
                });
                return;
            }
            this.$node.removeClass('loading-small-animate');

            ReactDOM.render(React.createElement(DiffPanel, {
                flatDiffs: this.flatDiffs,
                formatLabel: this.formatLabel,
                onPublishClick: this.onMarkPublish,
                onUndoClick: this.onMarkUndo,
                onSelectAllPublishClick: this.onSelectAllPublish,
                onSelectAllUndoClick: this.onSelectAllUndo,
                onDeselectAllClick: this.onDeselectAll,
                publishing: this.publishing,
                undoing: this.undoing,
                onApplyPublishClick: this.onApplyPublishClick,
                onApplyUndoClick: this.onApplyUndoClick,
                onVertexRowClick: this.onVertexRowClick,
                onEdgeRowClick: this.onEdgeRowClick,
                ...this.renderCounts
            }), this.$node[0]);
        };

        this.before('teardown', function() {
            ReactDOM.unmountComponentAtNode(this.node);
        })

        this.after('initialize', function() {
            var self = this;

            [
                'onMarkPublish', 'onSelectAll', 'onSelectAllPublish',
                'onSelectAllUndo', 'onMarkUndo', 'onDeselectAll',
                'onApplyPublishClick', 'onApplyUndoClick',
                'onVertexRowClick', 'onEdgeRowClick'
            ].forEach(m => {
                this[m] = this[m].bind(this);
            })

            this.dataRequest('ontology', 'ontology').done(function(ontology) {
                self.ontologyConcepts = ontology.concepts;
                self.ontologyProperties = ontology.properties;
                self.ontologyRelationships = ontology.relationships;
                self.formatLabel = function(name) {
                    return self.ontologyProperties.byTitle[name].displayName;
                };
                self.setup();
            })
        });

        this.setDiffs = function(diffs) {
            this.diffs = diffs;
            this.flatDiffs = this.diffs.reduce((flat, diff, i) => {
                const { type } = diff.action;
                return [...flat, diff, ...diff.properties];
            }, []);
            this.updateCounts();
        }

        this.updateCounts = function() {
            let totalCount = 0, ontologyRequiredCount = 0;
            const { publishCount, undoCount } = this.flatDiffs.reduce(({ publishCount, undoCount }, diff) => {
                const { action, publish, undo, requiresOntologyPublish, requiresOntologyPublishProperty } = diff;
                let inc = 0;

                if (!action || action.type !== 'update') {
                    inc = 1;
                    totalCount++;

                    if (requiresOntologyPublish || requiresOntologyPublishProperty) {
                        ontologyRequiredCount++;
                    }
                }
                return {
                    publishCount: publish ? publishCount + inc : publishCount,
                    undoCount: undo ? undoCount + inc : undoCount
                }
            }, { publishCount: 0, undoCount: 0 });

            this.renderCounts = { publishCount, undoCount, totalCount, ontologyRequiredCount };
        }

        this.setup = function() {
            var self = this;
            this.setDiffs([]);

            self.processDiffs(self.attr.diffs).done(function(processDiffs) {
                self.setDiffs(processDiffs)
                self.render();
            });

            self.on('diffsChanged', function(event, data) {
                self.processDiffs(data.diffs).done(function(processDiffs) {
                    self.setDiffs(processDiffs);
                    self.render();
                });
            })
            self.on(document, 'objectsSelected', self.onObjectsSelected);
            self.on('click', { schemaTypeSelector: self.onSchemaTypeChange });
        };

        this.processDiffs = function(diffs) {
            var self = this,
                referencedVertices = [],
                referencedEdges = [],
                groupedByElement = _.groupBy(diffs, function(diff) {
                    if (diff.elementType === 'vertex' || diff.type === 'VertexDiffItem') {
                        referencedVertices.push(diff.vertexId || diff.elementId || diff.outVertexId);
                    } else if (diff.elementType === 'edge' || diff.type === 'EdgeDiffItem') {
                        referencedEdges.push(diff.edgeId || diff.elementId);
                    }
                    if (diff.inVertexId) {
                        referencedVertices.push(diff.inVertexId);
                    }
                    if (diff.outVertexId) {
                        referencedVertices.push(diff.outVertexId);
                    }
                    if (diff.vertexId) return diff.vertexId;
                    if (diff.edgeId) return diff.edgeId;
                    if (diff.elementId) return diff.elementId;
                    return diff.outVertexId;
                }),
                output = [];

            return Promise.all([
                visalloData.storePromise,
                visalloData.selectedObjectsPromise()
            ]).spread(function(store, selectedObjects) {
                var state = store.getState(),
                    workspaceId = state.workspace.currentId,
                    elementStore = state.element[workspaceId] || {},
                    verticesById = elementStore.vertices || {},
                    edgesById = elementStore.edges || {},
                    selectedById = selectedObjects.vertices.concat(selectedObjects.edges)
                        .map(function(object) { return object.id; })
                        .reduce(function(selected, id) {
                            selected[id] = true;
                            return selected;
                        }, {}),
                    previousDiffsById = self.diffsById || {};
                    self.diffsForElementId = {};
                    self.diffsById = {};
                    self.diffDependencies = {};
                    self.undoDiffDependencies = {};

                    _.each(groupedByElement, function(diffs, elementId) {
                        var actionTypes = {
                                CREATE: { type: 'create', display: i18n('workspaces.diff.action.types.create') },
                                UPDATE: { type: 'update', display: i18n('workspaces.diff.action.types.update') },
                                DELETE: { type: 'delete', display: i18n('workspaces.diff.action.types.delete') }
                            },
                            outputItem = {
                                properties: [],
                                action: {},
                                active: selectedById[elementId],
                                publish: previousDiffsById[elementId] && previousDiffsById[elementId].publish,
                                undo: previousDiffsById[elementId] && previousDiffsById[elementId].undo,
                                className: F.className.to(elementId)
                            },
                            isElementVertex = (
                                diffs[0].elementType === 'vertex' ||
                                diffs[0].type === 'VertexDiffItem'
                            );

                        if (isElementVertex) {
                            outputItem.vertexId = elementId;
                            outputItem.vertex = verticesById[elementId];
                            const conceptType = diffs[0].conceptType || diffs[0].elementConcept;
                            if (outputItem.vertex) {
                                outputItem.title = F.vertex.title(outputItem.vertex);
                            } else {
                                outputItem.vertex = {
                                    id: elementId,
                                    type: 'vertex',
                                    properties: [],
                                    conceptType,
                                    'http://visallo.org#visibilityJson': diffs[0].visibilityJson
                                };
                                outputItem.title = diffs[0].title;
                            }
                            if (conceptType) {
                                var concept = self.ontologyConcepts.byId[conceptType];
                                if (concept) {
                                    outputItem.concept = concept;
                                    outputItem.conceptImage = concept.glyphIconHref;
                                    outputItem.selectedConceptImage = concept.glyphIconSelectedHref || concept.glyphIconHref;
                                    if (concept.sandboxStatus !== 'PUBLIC') {
                                        outputItem.requiresOntologyPublish = true;
                                    }
                                }
                            }
                        } else {
                            outputItem.edgeId = elementId;
                            outputItem.edge = edgesById[elementId];
                            if (outputItem.edge) {
                                outputItem.edgeLabel = self.ontologyRelationships.byTitle[outputItem.edge.label].displayName;
                            } else {
                                outputItem.edge = {
                                    id: elementId,
                                    type: 'edge',
                                    properties: [],
                                    'http://visallo.org#visibilityJson': diffs[0].visibilityJson
                                };
                            }

                            const label = diffs[0].label || diffs[0].elementConcept;
                            if (label) {
                                const relationship = self.ontologyRelationships.byId[label];
                                if (relationship) {
                                    outputItem.edgeLabel = relationship.displayName;
                                    outputItem.relationship = relationship;
                                    if (relationship.sandboxStatus !== 'PUBLIC') {
                                        outputItem.requiresOntologyPublish = true;
                                    }
                                }
                            }

                            var sourceId = diffs[0].outVertexId,
                                targetId = diffs[0].inVertexId,
                                source = verticesById[sourceId],
                                target = verticesById[targetId];
                            outputItem.sourceId = sourceId;
                            outputItem.targetId = targetId;
                            outputItem.sourceTitle = titleForEdgesVertices(source, sourceId, groupedByElement);
                            outputItem.targetTitle = titleForEdgesVertices(target, targetId, groupedByElement);
                        }

                        diffs.forEach(function(diff) {

                            switch (diff.type) {
                                case 'VertexDiffItem':
                                    diff.id = elementId;
                                    diff.publish = outputItem.publish;
                                    diff.undo = outputItem.undo;
                                    diff.requiresOntologyPublish = outputItem.requiresOntologyPublish;
                                    outputItem.action = diff.deleted ? actionTypes.DELETE : actionTypes.CREATE;
                                    self.diffsForElementId[elementId] = diff;
                                    self.diffsById[elementId] = diff;
                                    addDiffDependency(diff.id);
                                    break;

                                case 'PropertyDiffItem':
                                    var ontologyProperty = self.ontologyProperties.byTitle[diff.name];
                                    var compoundProperty = self.ontologyProperties.byDependentToCompound[diff.name];
                                    var isDependent = !!diff.dependentName;

                                    if (ontologyProperty && (ontologyProperty.userVisible || ontologyProperty.title === COMMENT_ENTRY_IRI)) {
                                        if (!isDependent) {
                                            diff.id = elementId + diff.name + diff.key;
                                            diff.publish = previousDiffsById[diff.id] && previousDiffsById[diff.id].publish;
                                            diff.undo = previousDiffsById[diff.id] && previousDiffsById[diff.id].undo;
                                            addDiffDependency(diff.elementId, diff);
                                            diff.className = F.className.to(diff.id);
                                        }

                                        diff.requiresOntologyPublishProperty = ontologyProperty.sandboxStatus !== 'PUBLIC';
                                        diff.requiresOntologyPublish = outputItem.requiresOntologyPublish || diff.requiresOntologyPublishProperty;
                                        if (diff.requiresOntologyPublishProperty) {
                                            diff.property = ontologyProperty;
                                        }

                                        if (compoundProperty &&
                                            F.vertex.hasProperty(outputItem.vertex, compoundProperty)) {

                                            diff.dependentName = diff.name;
                                            diff.name = compoundProperty;
                                            var previousPropertyWithKey = _.findWhere(outputItem.properties, {
                                                key: diff.key,
                                                name: diff.name
                                            })
                                            if (previousPropertyWithKey) {
                                                if (previousPropertyWithKey.old) {
                                                    previousPropertyWithKey.old.push(diff.old);
                                                }
                                                if (previousPropertyWithKey.new) {
                                                    previousPropertyWithKey.new.push(diff.new);
                                                }
                                                previousPropertyWithKey.diffs.push(diff)
                                            } else {
                                                if (diff.old) {
                                                    diff.old = [diff.old];
                                                }
                                                if (diff.new) {
                                                    diff.new = [diff.new];
                                                }
                                                diff.diffs = [diff];
                                                outputItem.properties.push(diff)
                                            }
                                        } else {
                                            outputItem.properties.push(diff)
                                        }
                                        self.diffsById[diff.id] = diff;
                                    }
                                    break;

                                case 'EdgeDiffItem':
                                    diff.id = diff.edgeId;
                                    diff.publish = outputItem.publish;
                                    diff.undo = outputItem.undo;
                                    diff.inVertex = verticesById[diff.inVertexId];
                                    diff.outVertex = verticesById[diff.outVertexId];
                                    diff.className = F.className.to(diff.edgeId);
                                    diff.displayLabel = self.ontologyRelationships.byTitle[diff.label].displayName;
                                    diff.requiresOntologyPublish = outputItem.requiresOntologyPublish;
                                    self.diffsForElementId[diff.edgeId] = diff;
                                    outputItem.action = diff.deleted ? actionTypes.DELETE : actionTypes.CREATE;
                                    addDiffDependency(diff.inVertexId, diff);
                                    addDiffDependency(diff.outVertexId, diff);
                                    self.diffsById[diff.id] = diff;
                                    break;

                                default:
                                    console.warn('Unknown diff item type', diff.type)
                            }

                            addDiffDependency(diff.id);
                        });

                        if (_.isEmpty(outputItem.action)) {
                            outputItem.action = actionTypes.UPDATE;
                        }

                        output.push(outputItem);
                    });

                    return output;
            });

            function addDiffDependency(id, diff) {
                if (!self.diffDependencies[id]) {
                    self.diffDependencies[id] = [];
                }
                if (diff) {
                    self.diffDependencies[id].push(diff.id);

                    // Undo dependencies are inverse
                    if (!self.undoDiffDependencies[diff.id]) {
                        self.undoDiffDependencies[diff.id] = [];
                    }
                    self.undoDiffDependencies[diff.id].push(id);
                }
            }
        };

        this.onObjectsSelected = function(event, data) {
            var vertices = data.vertices,
                edges = data.edges;

            this.diffs.forEach(function(diff) {
                diff.active = _.findWhere(vertices, { id: diff.vertexId }) || _.findWhere(edges, { id: diff.edgeId });
            });
            this.render();
        };

        this.onVertexRowClick = function(vertexId) {
            this.trigger('selectObjects', {
                vertexIds: vertexId ? [vertexId] : []
            });
        };

        this.onEdgeRowClick = function(edgeId) {
            this.trigger('selectObjects', {
                edgeIds: edgeId ? [edgeId] : []
            });
        };

        this.onDeselectAll = function() {
            this.resetWarning();

            var self = this;
            this.diffs.forEach(function(diff) {
                deselectAction(diff);
                diff.properties.forEach(function(property) {
                    deselectAction(property);
                });
            });
            Object.keys(this.diffsById).forEach(function(id) {
                deselectAction(self.diffsById[id]);
            });
            this.updateCounts();
            this.render();

            function deselectAction(diff, action) {
                diff.publish = false;
                diff.undo = false;
            }
        };

        this.onSelectAll = function(action) {
            const canPublishOntology = Boolean(visalloData.currentUser.privilegesHelper.ONTOLOGY_PUBLISH);

            this.resetWarning();

            this.diffs
                .forEach((diff) => {
                    if (allowSelect(diff, action)) {
                        selectAction(diff, action);
                        diff.properties.forEach((property) => {
                            selectAction(property, action)
                        });
                    } else if (action === 'publish') {
                        diff.undo = false;
                    }
                });

            _.values(this.diffsById).forEach(diff => {
                if (allowSelect(diff, action)) {
                    selectAction(diff, action);
                } else if (action === 'publish') {
                    diff.undo = false;
                }
            });
            this.updateCounts();
            this.render();

            function allowSelect(diff, action) {
                return (
                    (!diff.action || diff.action.type !== 'update')
                    && (
                        action === 'undo'
                        || !(diff.requiresOntologyPublish || diff.requiresOntologyPublishProperty)
                        || canPublishOntology
                    )
                );
            }
            function selectAction(diff, action) {
                diff.publish = false;
                diff.undo = false;

                if (action === 'undo'
                    || !(diff.requiresOntologyPublish || diff.requiresOntologyPublishProperty)
                    || canPublishOntology) {
                    diff[action] = true;
                }
            }
        };

        this.onSelectAllPublish = _.partial(this.onSelectAll, 'publish');
        this.onSelectAllUndo = _.partial(this.onSelectAll, 'undo');

        this.onSchemaTypeChange = function(event) {
            const $li = $(event.target).closest('li')
            const diffIds = JSON.parse($li.attr('data-diff-ids'));

            diffIds.forEach(id => {
                this.onMarkPublish(id, false);
            })

            _.defer(() => {
                var ontologyToPublish = this.buildOntologyToPublish();
                if (_.some(ontologyToPublish, _.size)) {
                    this.skipSchemaWarning = true;
                    this.handleSchemaChanges(ontologyToPublish);
                }
            })
        };

        this.handleSchemaChanges = function(toPublish) {
            require(['./schemaWarning.hbs'], tpl => {
                const makeList = (name, displayName, diffId, lookup) => {
                    const size = _.size(toPublish[name]);
                    if (size) {
                        return {
                            displayName,
                            size,
                            list:  _.map(toPublish[name], (diffs, id) => ({
                                displayName: lookup[id].displayName,
                                usages: F.string.plural(diffs.length, 'usage'),
                                diffs: JSON.stringify(diffs.map(d => d[diffId]))
                            }))
                        }
                    }
                }
                const html = tpl({
                    types: _.compact([
                        makeList('concepts', i18n('workspaces.diff.schema.warning.type.vertex'), 'vertexId', this.ontologyConcepts.byId),
                        makeList('relationships', i18n('workspaces.diff.schema.warning.type.edge'), 'edgeId', this.ontologyRelationships.byTitle),
                        makeList('properties', i18n('workspaces.diff.schema.warning.type.property'), 'id', this.ontologyProperties.byTitle)
                    ])
                })

                var error = $('<div>')
                    .addClass('alert alert-warning')
                    .html(html)
                    .prependTo(this.$node.find('.diff-alerts').empty())
                    .alert();
            })
        };

        this.onApplyAll = function(type, publishOntology) {
            const self = this;
            const publishing = type === 'publish';
            const canPublishOntology = Boolean(visalloData.currentUser.privilegesHelper.ONTOLOGY_PUBLISH);

            if (publishing && !publishOntology && canPublishOntology) {
                var ontologyToPublish = this.buildOntologyToPublish();
                if (_.some(ontologyToPublish, _.size)) {
                    if (this.skipSchemaWarning) {
                        this.skipSchemaWarning = false;
                        this.onApplyAll(type, true);
                    } else {
                        this.skipSchemaWarning = true;
                        this.handleSchemaChanges(ontologyToPublish);
                    }
                    return;
                }
            }

            this.resetWarning();

            var diffsToSend = this.buildDiffsToSend(type);
            this.publishing = publishing;
            this.undoing = type === 'undo';
            this.render();

            this.dataRequest('workspace', type, diffsToSend)
                .finally(function() {
                    self.publishing = self.undoing = false;
                    self.trigger(document, 'updateDiff');
                })
                .then(function(response) {
                    var failures = response.failures,
                        success = response.success,
                        nextDiffs = self.buildNextDiffs(type, failures);

                    return self
                        .processDiffs(nextDiffs)
                        .then(function(processDiffs) {
                            if (processDiffs.length) {
                                self.setDiffs(processDiffs);
                                self.render();

                                if (failures && failures.length) {
                                    var error = $('<div>')
                                        .addClass('alert alert-error')
                                        .html(
                                            '<button type="button" class="close" data-dismiss="alert">&times;</button>' +
                                            '<ul><li>' + _.pluck(failures, 'errorMessage').join('</li><li>') + '</li></ul>'
                                        )
                                        .prependTo(self.$node.find('.diff-alerts').empty())
                                        .alert();
                                }

                                if (type === 'undo') {
                                    self.trigger('loadCurrentWorkspace');
                                }
                            } else {
                                self.trigger('toggleDiffPanel');
                            }
                        });
                })
                .catch(function(errorText) {
                    self.render();

                    var error = $('<div>')
                        .addClass('alert alert-error')
                        .html(
                            '<button type="button" class="close" data-dismiss="alert">&times;</button>' +
                            i18n('workspaces.diff.error', type, errorText)
                        )
                        .prependTo(self.$node.find('.diff-alerts').empty())
                        .alert();
                });
        };
        this.onApplyPublishClick = _.partial(this.onApplyAll, 'publish', false);
        this.onApplyUndoClick = _.partial(this.onApplyAll, 'undo', false);

        this.resetWarning = function() {
            this.$node.find('.diff-alerts').empty();
            this.skipSchemaWarning = false;
        }

        this.buildOntologyToPublish = function() {
            const currentUserId = visalloData.currentUser.id;
            const concepts = {};
            const relationships = {};
            const properties = {};
            const ontologyObjectCreatedByCurrentUser = ontology => {
                if (ontology.metadata) {
                    const modifiedBy = ontology.metadata['http://visallo.org#modifiedBy'];
                    if (modifiedBy === currentUserId) {
                        return true
                    }
                }
            }
            const add = (obj, o, diff) => {
                if (!ontologyObjectCreatedByCurrentUser(o)) {
                    if (!obj[o.title]) {
                        obj[o.title] = [];
                    }
                    obj[o.title].push(diff)
                }
            };
            this.diffs.forEach(diff => {
                if (diff.publish) {
                    if (diff.requiresOntologyPublish) {
                        if (diff.concept) add(concepts, diff.concept, diff);
                        if (diff.relationship) add(relationships, diff.relationship, diff);
                    }
                }
                diff.properties.forEach(diff => {
                    if (diff.publish && diff.requiresOntologyPublishProperty && diff.property) {
                        add(properties, diff.property, diff)
                    }
                })
            })

            return { concepts, relationships, properties };
        };

        this.buildDiffsToSend = function(applyType) {
            const self = this;
            const publishing = applyType === 'publishing';
            const canPublishOntology = Boolean(visalloData.currentUser.privilegesHelper.ONTOLOGY_PUBLISH);
            let diffsToSend = [];

            this.diffs
                .filter(diff => {
                    return !publishing
                        || !(diff.requiresOntologyPublish || diff.requiresOntologyPublishProperty)
                        || canPublishOntology;
                }).forEach(function(diff) {
                    const vertexId = diff.vertexId;
                    const edgeId = diff.edgeId;
                    const properties = diff.properties;

                    if (diff[applyType]) {
                        if (diff.vertex) {
                            diffsToSend.push(vertexDiffToSend(diff));
                        } else if (diff.edge) {
                            diffsToSend.push(edgeDiffToSend(diff));
                        }
                        diff.applying = self.diffsById[vertexId || edgeId].applying = true;
                    }

                    properties
                        .filter(function(property) { return property[applyType]; })
                        .forEach(function(property) {
                            property.applying = self.diffsById[property.id].applying = true;
                            if (property.diffs) {
                                diffsToSend = diffsToSend.concat(property.diffs.map(propertyDiffToSend))
                            } else {
                                diffsToSend.push(propertyDiffToSend(property));
                            }
                        });
                });

            return diffsToSend;

            function vertexDiffToSend(diff) {
                var vertex = self.diffsById[diff.vertexId];

                return {
                    type: 'vertex',
                    vertexId: diff.vertexId,
                    action: vertex.deleted ? 'delete' : 'create',
                    status: vertex.sandboxStatus
                };
            }

            function edgeDiffToSend(diff) {
                var edge = self.diffsById[diff.edgeId];

                return {
                    type: 'relationship',
                    edgeId: diff.edgeId,
                    sourceId: edge.outVertexId,
                    destId: edge.inVertexId,
                    action: edge.deleted ? 'delete' : 'create',
                    status: edge.sandboxStatus
                };
            }

            function propertyDiffToSend(diff) {
                var diffToSend = {
                    type: 'property',
                    key: diff.key,
                    name: diff.dependentName || diff.name,
                    action: diff.deleted ? 'delete' : 'update',
                    status: diff.sandboxStatus
                };
                diffToSend[diff.elementType + 'Id'] = diff.elementId;
                return diffToSend;
            }
        };

        this.buildNextDiffs = function(applyType, failures) {
            var self = this,
                failuresById = failures.reduce(function(failures, failure) {
                    var type = failure.type,
                        vertexId = failure.vertexId,
                        edgeId = failure.edgeId,
                        name = failure.name,
                        key = failure.key,
                        id;

                    switch (type) {
                        case 'vertex':
                            id = failure.vertexId;
                            break;
                        case 'relationship':
                            id = failure.edgeId;
                            break;
                        case 'property':
                            id = (vertexId || edgeId) + name + key;
                            break;
                    }
                    failures[id] = true;
                    return failures;
                }, {});
            return Object.keys(self.diffsById)
                .map(function(id) {
                    return self.diffsById[id];
                })
                .reduce(function(diffsToProcess, diff) {
                    if (!diff.applying || failuresById[diff.id]) {
                        diffsToProcess.push(diff);
                    }
                    diff[applyType] = diff.applying ? failuresById[diff.id] : diff[applyType];
                    diff.applying = false;
                    return diffsToProcess;
                }, []);
        };

        this.onMarkUndo = function(diffId, state) {
            var self = this,
                diff = this.diffsById[diffId],
                deps = this.diffDependencies[diffId] || [],
                vertexDiff;
            state = state === undefined ? !diff.undo : state;

            this.resetWarning();

            switch (diff.type) {
                case 'VertexDiffItem':
                    vertexDiff = _.findWhere(this.diffs, { vertexId: diffId});
                    vertexDiff.undo = diff.undo = state;
                    vertexDiff.publish = diff.publish = false;

                    if (state) {
                        if (!diff.deleted) {
                            deps.forEach(function(diffId) {
                                self.onMarkUndo(diffId, true);
                                self.trigger('markUndoDiffItem', { diffId: diffId, state: true });
                            })
                        }
                    }

                    break;

                case 'PropertyDiffItem':
                    var byId = {};
                    byId[diff.elementType + 'Id'] = diff.elementId;
                    var propertyDiff = _.chain(this.diffs)
                        .findWhere(byId)
                        .reduce(function(result, val, key) { return key === 'properties' ? val : result})
                        .findWhere({ id: diffId })
                        .value();

                    if (propertyDiff) {
                        propertyDiff.undo = diff.undo = state;
                        propertyDiff.publish = diff.publish = false;
                    }

                    if (!state) {
                        vertexDiff = self.diffsForElementId[diff.elementId];
                        if (vertexDiff && vertexDiff.undo) {
                            self.onMarkUndo(vertexDiff.id, false);
                            self.trigger('markUndoDiffItem', { diffId: vertexDiff.id, state: false });
                        }
                    }

                    break;

                case 'EdgeDiffItem':
                    var edgeDiff = _.findWhere(this.diffs, { edgeId: diffId });
                    edgeDiff.undo = diff.undo = state;
                    edgeDiff.publish = diff.publish = false;

                    var inVertex = self.diffsForElementId[diff.inVertexId],
                        outVertex = self.diffsForElementId[diff.outVertexId];

                    if (state) {
                        if (diff.deleted) {
                            if (inVertex && inVertex.deleted) {
                                self.onMarkUndo(inVertex.id, true);
                                self.trigger('markUndoDiffItem', { diffId: inVertex.id, state: true });
                            }
                            if (outVertex && outVertex.deleted) {
                                self.onMarkUndo(outVertex.id, true);
                                self.trigger('markUndoDiffItem', { diffId: outVertex.id, state: true });
                            }
                        } else {
                            deps.forEach(function(diffId) {
                                self.onMarkUndo(diffId, true);
                                self.trigger('markUndoDiffItem', { diffId: diffId, state: true });
                            })
                        }
                    } else {
                        if (inVertex) {
                            self.onMarkUndo(inVertex.id, false);
                            self.trigger('markUndoDiffItem', { diffId: inVertex.id, state: false });
                        }

                        if (outVertex) {
                            self.onMarkUndo(outVertex.id, false);
                            self.trigger('markUndoDiffItem', { diffId: outVertex.id, state: false });
                        }
                    }

                    break;

                default: console.warn('Unknown diff item type', diff.type)
            }
            this.updateCounts();
            this.render();
        };

        this.onMarkPublish = function(diffId, state) {
            var self = this,
                diff = this.diffsById[diffId],
                canPublishOntology = Boolean(visalloData.currentUser.privilegesHelper.ONTOLOGY_PUBLISH),
                vertexDiff;
            state = state === undefined ? !diff.publish : state;

            if (diff.requiresOntologyPublish && !canPublishOntology) {
                return;
            }

            this.resetWarning();

            switch (diff.type) {

                case 'VertexDiffItem':
                    vertexDiff = _.findWhere(this.diffs, { vertexId: diffId });
                    vertexDiff.publish = diff.publish = state;
                    vertexDiff.undo = diff.undo = false;
                    if (state && diff.deleted) {
                        this.diffDependencies[diff.id].forEach(function(diffId) {
                            var diff = self.diffsById[diffId];
                            if (diff && diff.type === 'EdgeDiffItem' && diff.deleted) {
                                self.onMarkPublish(diffId, true);
                                self.trigger('markPublishDiffItem', { diffId: diffId, state: true });
                            } else {
                                self.onMarkPublish(diffId, false);
                                self.trigger('markPublishDiffItem', { diffId: diffId, state: false });
                            }
                        });
                    } else if (!state) {
                        this.diffDependencies[diff.id].forEach(function(diffId) {
                            self.onMarkPublish(diffId, false);
                            self.trigger('markPublishDiffItem', { diffId: diffId, state: false });
                        });
                    }

                    break;

                case 'PropertyDiffItem':
                    var byId = {};
                    byId[diff.elementType + 'Id'] = diff.elementId;
                    var propertyDiff = _.chain(this.diffs)
                        .findWhere(byId)
                        .reduce(function(result, val, key) { return key === 'properties' ? val : result})
                        .findWhere({ id: diffId })
                        .value();

                    if (propertyDiff) {
                        propertyDiff.publish = diff.publish = state;
                        propertyDiff.undo = diff.undo = false;
                    }

                    if (state) {
                        vertexDiff = this.diffsForElementId[diff.elementId];
                        if (vertexDiff && !vertexDiff.deleted) {
                            self.onMarkPublish(diff.elementId, true);
                            this.trigger('markPublishDiffItem', { diffId: diff.elementId, state: true })
                        }
                    }

                    break;

                case 'EdgeDiffItem':
                    var edgeDiff = _.findWhere(this.diffs, { edgeId: diffId });
                    edgeDiff.publish = diff.publish = state;
                    edgeDiff.undo = diff.undo = false;

                    if (!state) {
                        // Unpublish all dependents
                        this.diffDependencies[diff.id].forEach(function(diffId) {
                            self.onMarkPublish(diffId, false);
                            self.trigger('markPublishDiffItem', { diffId: diffId, state: false });
                        });
                    } else {
                        var inVertexDiff = this.diffsForElementId[diff.inVertexId],
                            outVertexDiff = this.diffsForElementId[diff.outVertexId];

                        if (inVertexDiff && !diff.deleted) {
                            self.onMarkPublish(diff.inVertexId, true);
                            this.trigger('markPublishDiffItem', { diffId: diff.inVertexId, state: true });
                        }
                        if (outVertexDiff && !diff.deleted) {
                            self.onMarkPublish(diff.outVertexId, true);
                            this.trigger('markPublishDiffItem', { diffId: diff.outVertexId, state: true });
                        }
                    }

                    break;

                default: console.warn('Unknown diff item type', diff.type)
            }
            this.updateCounts();
            this.render();
        };
    }
});
