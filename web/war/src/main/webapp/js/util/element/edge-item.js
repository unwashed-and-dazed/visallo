define([
    'flight/lib/component',
    './edge-item.hbs',
    'util/requirejs/promise!util/service/ontologyPromise',
    'util/vertex/justification/viewer',
    'util/withDataRequest',
    'util/vertex/formatters'
], function (defineComponent,
             template,
             ontology,
             JustificationViewer,
             withDataRequest,
             F) {
    'use strict';

    return defineComponent(EdgeItem, withDataRequest);

    function EdgeItem() {

        this.after('initialize', function () {
            const edge = this.attr.item,
                ontologyRelation = ontology.relationships.byTitle[edge.label],
                title = ontologyRelation.titleFormula ? F.edge.title(edge) : ontologyRelation.displayName,
                subtitle = ontologyRelation.subtitleFormula ? F.edge.subtitle(edge) : null,
                timeSubtitle = ontologyRelation.timeFormula ? F.edge.time(edge) : null;

            this.$node.data('edgeId', edge.id);

            Promise.all([
                this.dataRequest('vertex', 'store', {vertexIds: [edge.inVertexId, edge.outVertexId]}),
                this.dataRequest('config', 'properties')
            ])
                .spread((vertices, properties) => {
                    const inVertex = _.findWhere(vertices, {id: edge.inVertexId});
                    const outVertex = _.findWhere(vertices, {id: edge.outVertexId});
                    this.$node
                        .addClass('default')
                        .addClass('edge-item')
                        .addClass(timeSubtitle ? 'has-timeSubtitle' : '')
                        .addClass(subtitle ? 'has-subtitle' : '')
                        .html(template({
                            title: title,
                            timeSubtitle: timeSubtitle,
                            subtitle: subtitle,
                            inVertex: this.getData(inVertex),
                            outVertex: this.getData(outVertex),
                        }));

                    if (properties['field.justification.validation'] !== 'NONE' &&
                        this.attr.usageContext === 'detail/multiple') {
                        this.renderJustification();
                    }
                });
        });

        this.getData = function (vertex) {
            if (!vertex) {
                return {
                    title: i18n('element.unauthorized'),
                    image: 'img/glyphicons/glyphicons_194_circle_question_mark@2x.png',
                    custom: false
                }
            }
            return {
                title: F.vertex.title(vertex),
                image: F.vertex.image(vertex, null, 80),
                custom: !F.vertex.imageIsFromConcept(vertex)
            };
        };

        this.renderJustification = function () {
            const edge = this.attr.item,
                titleSpan = this.$node.children('span.title'),
                justification = _.findWhere(edge.properties, {name: 'http://visallo.org#justification'}),
                sourceInfo = _.findWhere(edge.properties, {name: '_sourceMetadata'});

            if (justification || sourceInfo) {
                titleSpan.empty();
                JustificationViewer.attachTo(titleSpan, {
                    justificationMetadata: justification && justification.value,
                    sourceMetadata: sourceInfo && sourceInfo.value
                });
            }
        };

        this.before('teardown', function () {
            this.$node.removeData('edgeId');
            this.$node.removeClass('edge-item has-timeSubtitle has-subtitle');
            this.$node.empty();
        });
    }
});
