define(['reselect'], function(reselect) {
    const { createSelector } = reselect;

    const THING = 'http://www.w3.org/2002/07/owl#Thing';
    const ROOT = 'http://visallo.org#root';
    const EDGE_THING = 'http://www.w3.org/2002/07/owl#topObjectProperty';

    const _visible = (item, options = {}) => {
        const { rootItemsHidden = true } = options;
        return item &&
            item.userVisible !== false &&
            (!rootItemsHidden || (item.id !== EDGE_THING && item.id !== THING && item.id !== ROOT)) &&
            item.displayName;
    };
    const _collectParents = (concepts, { parentKey, extraKeys = [], defaults = {} } = {}) => concept => {
        const collecting = {
            path: [],
            fullPath: [],
            properties: [],
            ...(_.object(extraKeys.map(k => [k, null])))
        };
        _collect(concept);
        const {
            path,
            fullPath,
            properties,
            ...override } = collecting;
        _.each(override, (v, k) => {
            if (!v && defaults[k]) {
                override[k] = defaults[k];
            }
        })
        const newConcept = {
            ...concept,
            path: '/' + path.reverse().join('/'),
            fullPath: '/' + fullPath.reverse().join('/'),
            properties: _.uniq(properties),
            depth: path.length - 1,
            fullDepth: fullPath.length - 1,
            ...override
        };
        return newConcept;

        function _collect(concept) {
            extraKeys.forEach(k => {
                collecting[k] = collecting[k] || concept[k];
            })
            if (_visible(concept, { rootItemsHidden: false })) {
                collecting.fullPath.push(concept.displayName)
            }
            if (_visible(concept)) {
                collecting.path.push(concept.displayName)
            }
            collecting.properties = collecting.properties.concat(concept.properties);

            if (concept[parentKey]) {
                const parent = concepts[concept[parentKey]];
                _collect(parent);
            }
        }
    }

    const _propertiesWithHeaders = (properties) => {
        let lastGroup;
        return properties.reduce(
            (properties, property) => {
                const { propertyGroup } = property;
                if (propertyGroup && lastGroup !== propertyGroup) {
                    lastGroup = propertyGroup;
                    return [
                        ...properties,
                        {
                            displayName: propertyGroup,
                            header: true
                        },
                        property
                    ];
                }
                return [...properties, property];
            },
            []
        );
    }

    const getWorkspace = (state) => state.workspace.currentId;

    const getOntologyRoot = (state) => state.ontology;

    const getConcepts = createSelector([getWorkspace, getOntologyRoot], (workspaceId, ontology) => {
        const concepts = ontology[workspaceId].concepts;
        const fn = _collectParents(concepts, {
            parentKey: 'parentConcept',
            extraKeys: ['color', 'displayType', 'glyphIconHref', 'glyphIconSelectedHref', 'titleFormula', 'subtitleFormula', 'timeFormula', 'validationFormula'],
            defaults: { glyphIconHref: 'img/glyphicons/glyphicons_194_circle_question_mark@2x.png' }
        });
        return _.mapObject(concepts, c => {
            return { ...fn(c), displayNameSub: '' };
        });
    })

    const getProperties = createSelector([getWorkspace, getOntologyRoot], (workspaceId, ontology) => {
        return ontology[workspaceId].properties;
    })

    const getRelationships = createSelector([getWorkspace, getOntologyRoot, getConcepts], (workspaceId, ontology, concepts) => {
        const relationships = ontology[workspaceId].relationships;
        const getSortedConcepts = iris => _.chain(iris)
            .map(iri => concepts[iri])
            .sortBy('displayName')
            .sortBy('depth')
            .value();
        const fn = _collectParents(relationships, { parentKey: 'parentIri' });
        return _.omit(_.mapObject(relationships, r => {
            const newR = fn(r);
            const domains = getSortedConcepts(newR.domainConceptIris);
            const ranges = getSortedConcepts(newR.rangeConceptIris);
            if (domains.length === 0 && ranges.length === 0) return null;
            const domainGlyphIconHref = domains[0].glyphIconHref;
            const rangeGlyphIconHref = ranges[0].glyphIconHref;
            const displayNameSub = domains.length === 1 ? ranges.map(r => domains[0].displayName + '→' + r.displayName).join('\n') :
                ranges.length === 1 ? domains.map(d => d.displayName + '→' + ranges[0].displayName).join('\n') :
                `(${domains.map(d => d.displayName).join(', ')}) → (${ranges.map(r => r.displayName).join(', ')})`
            return { ...newR, domainGlyphIconHref, rangeGlyphIconHref, displayNameSub };
        }), v => v === null);
    })

    const getVisibleRelationships = createSelector([getRelationships, getConcepts], (relationships, concepts) => {
        const anyIrisVisible = iris => _.isArray(iris) && _.any(iris, iri => _visible(concepts[iri], { rootItemsHidden: false }))
        const relationshipConceptsVisible = r => anyIrisVisible(r.rangeConceptIris) && anyIrisVisible(r.domainConceptIris);
        return _.chain(relationships)
            .map()
            .filter(r => _visible(r) && relationshipConceptsVisible(r))
            .sortBy('path')
            .value()
    })

    const getVisibleRelationshipsByConcept = createSelector([getVisibleRelationships], relationships => {
        const result = {};
        relationships.forEach(r => {
            ['domainConceptIris', 'rangeConceptIris'].forEach(key => {
                r[key].forEach(iri => {
                    if (!result[iri]) result[iri] = [];
                    if (!result[iri].includes(r.title)) {
                        result[iri].push(r.title);
                    }
                })
            })
        })
        return result;
    })

    const getOtherConcepts = createSelector([getVisibleRelationships], relationships => {
        const result = {};
        relationships.forEach(r => {
            r.domainConceptIris.forEach(d => {
                if (!result[d]) result[d] = [];
                result[d].push(...r.rangeConceptIris)
            })
            r.rangeConceptIris.forEach(d => {
                if (!result[d]) result[d] = [];
                result[d].push(...r.domainConceptIris)
            })
        })
        return result;
    })

    const getRelationshipAncestors = createSelector([getRelationships], relationships => {
        const byParent = _.groupBy(relationships, 'parentIri');
        const collectAncestors = (list, r, skipFirst) => {
            if (r) {
                if (!skipFirst) list.push(r.title);
                if (r.parentIri) {
                    collectAncestors(list, relationships[r.parentIri]);
                }
            }
            return _.uniq(list);
        }
        return _.mapObject(relationships, c => collectAncestors([], c, true));
    })

    const getRelationshipKeyIris = state => state.ontology.iris && state.ontology.iris.relationship;

    const getConceptAncestors = createSelector([getConcepts], concepts => {
        const byParent = _.groupBy(concepts, 'parentConcept');
        const collectAncestors = (list, c, skipFirst) => {
            if (c) {
                if (!skipFirst) list.push(c.title);
                if (c.parentConcept) {
                    collectAncestors(list, concepts[c.parentConcept]);
                }
            }
            return _.uniq(list);
        }
        return _.mapObject(concepts, c => collectAncestors([], c, true));
    })

    const getConceptDescendents = createSelector([getConcepts], concepts => {
        const byParent = _.groupBy(concepts, 'parentConcept');
        const collectDescendents = (list, c, skipFirst) => {
            if (!skipFirst) list.push(c.title);
            if (byParent[c.title]) {
                byParent[c.title].forEach(inner => collectDescendents(list, inner));
            }
            return _.uniq(list);
        }
        return _.mapObject(concepts, c => collectDescendents([], c, true));
    })

    const getConceptsList = createSelector([getConcepts], concepts => {
        return _.chain(concepts)
            .sortBy('fullPath')
            .value()
    })

    const getVisibleConceptsList = createSelector([getConcepts], concepts => {
        return _.chain(concepts)
            .filter(c => _visible(c))
            .sortBy('path')
            .value()
    })

    const getConceptsByRelatedConcept = createSelector([getVisibleConceptsList, getConceptAncestors, getOtherConcepts], (concepts, ancestors, otherConcepts) => {
        return _.chain(concepts)
            .map(topConcept => {
                var concepts = [];
                [topConcept.id, ...ancestors[topConcept.id]].forEach(iri => {
                    const other = otherConcepts[iri];
                    if (other) {
                        concepts.push(..._.uniq(other));
                    }
                })
                return [topConcept.id, concepts];
            })
            .object()
            .value();
    })

    const getConceptKeyIris = state => state.ontology.iris && state.ontology.iris.concept

    const getOntology = createSelector([getOntologyRoot, getWorkspace], (ontology, workspaceId) => ontology[workspaceId])

    const getPropertiesByConcept = createSelector([getConcepts, getProperties], (concepts, properties) => {
        return _.mapObject(concepts, r => {
            return _.pick(properties, r.properties);
        })
    })

    const getConceptProperties = createSelector([getPropertiesByConcept], (propertiesByConcept) => {
        let conceptProperties = {};

        return Object.keys(propertiesByConcept).reduce((properties, concept) => {
            return { ...properties, ...propertiesByConcept[concept] }
        }, conceptProperties);
    })

    const getPropertiesByRelationship = createSelector([getRelationships, getProperties], (relationships, properties) => {
        return _.mapObject(relationships, r => {
            return _.pick(properties, r.properties);
        })
    })

    const getRelationshipProperties = createSelector([getPropertiesByRelationship], (propertiesByRelationship) => {
        let relationshipProperties = {};

        return Object.keys(propertiesByRelationship).reduce((properties, relationship) => {
            return { ...properties, ...propertiesByRelationship[relationship] }
        }, relationshipProperties);
    })

    const getPropertiesByDependentToCompound = createSelector([getProperties], properties => {
        const dependentToCompounds = {};
        Object.keys(properties).forEach(iri => {
            const { dependentPropertyIris } = properties[iri];
            if (dependentPropertyIris) {
                dependentPropertyIris.forEach(dIri => {
                    const list = dependentToCompounds[dIri] || (dependentToCompounds[dIri] = [])
                    if (!list.includes(iri)) {
                        list.push(iri)
                    }
                })
            }
        })

        return dependentToCompounds;
    })

    const getPropertiesList = createSelector([getProperties], properties => {
        const compareNameAndGroup = ({ displayName, propertyGroup }) => {
            const displayNameLC = displayName.toLowerCase();
            return propertyGroup ? `1${propertyGroup}${displayNameLC}` : `0${displayNameLC}`;
        };

        return _.chain(properties)
            .sortBy(compareNameAndGroup)
            .value()
    })

    const getVisiblePropertiesList = createSelector([getPropertiesList], properties => {
        return _.filter(properties, _visible);
    });

    const getPropertyKeyIris = state => state.ontology.iris && state.ontology.iris.property;

    const getPropertiesWithHeaders = createSelector([getPropertiesList], properties => {
        return _propertiesWithHeaders(properties);

    })

    const getVisiblePropertiesWithHeaders = createSelector([getVisiblePropertiesList], properties => {
        return _propertiesWithHeaders(properties);
    });

    return {
        getOntology,

        getConcepts,
        getConceptKeyIris,
        getConceptDescendents,
        getConceptAncestors,
        getConceptsList,
        getConceptsByRelatedConcept,
        getVisibleConceptsList,

        getProperties,
        getPropertyKeyIris,
        getPropertiesByConcept,
        getConceptProperties,
        getPropertiesByRelationship,
        getRelationshipProperties,
        getPropertiesByDependentToCompound,
        getPropertiesList,
        getVisiblePropertiesList,
        getPropertiesWithHeaders,
        getVisiblePropertiesWithHeaders,

        getRelationships,
        getRelationshipAncestors,
        getRelationshipKeyIris,
        getVisibleRelationships,
        getVisibleRelationshipsByConcept
    }
});
