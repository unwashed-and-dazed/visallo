define([
    'create-react-class',
    'prop-types',
    'classnames',
    'components/element/Element',
    'util/vertex/formatters'
], function(createReactClass, PropTypes, classNames, Element, F) {

    const sharedProps = {
        refId: PropTypes.string.isRequired,
        id: PropTypes.string.isRequired,
        start: PropTypes.number.isRequired,
        end: PropTypes.number.isRequired,
        sandboxStatus: PropTypes.string.isRequired,
        outVertexId: PropTypes.string.isRequired,
        type: PropTypes.string.isRequired
    };
    const selectionProps = {
        mentionStart: PropTypes.number.isRequired,
        mentionEnd: PropTypes.number.isRequired,
        type: PropTypes.string.isRequired,
        snippet: PropTypes.string.isRequired
    };

    const url = src => `url(${src})`;
    const getSelectionText = snippet => {
        const el = document.createElement('div');
        el.innerHTML = snippet;
        return el.textContent;
    }

    const Icon = function(props) {

        const { src, isImage = false } = props;
        return (
            <div className={`icon${isImage ? ' image' : ''}`}>
                <div style={{ backgroundImage: url(src) }} />
            </div>
        );
    };
    const Actions = function(props) {
        const { actions } = props;
        if (actions.length) {
            return (
                <div className="buttons">
                    {actions.map(({ label, title, handler, classes = {} }) =>
                        (<button
                            title={title}
                            className={classNames('btn btn-mini', classes)}
                            key={label}
                            onClick={handler}>{label}</button>)
                    )}
                </div>
            )
        }
        return null;
    };
    const TermSelection = createReactClass({
        propTypes: {
            ...selectionProps
        },
        render() {
            const { onHoverTerm, onResolve, onProperty, onComment, canEdit, snippet } = this.props;
            const actions = canEdit ? [
                { label: i18n('detail.text.terms.resolve'), title: i18n('detail.text.terms.resolve.tooltip'), handler: this.onResolve, classes: { 'btn-success': true } },
                { label: i18n('detail.text.terms.property'), title: i18n('detail.text.terms.property.tooltip'), handler: onProperty },
                { label: i18n('detail.text.terms.comment'), title: i18n('detail.text.terms.comment.tooltip'), handler: onComment }
            ] : [];

            return (
                <li className="termselection" onMouseEnter={onHoverTerm}>
                    <Icon src={'../img/glyphicons_custom/selection.png'} />
                    <section>
                        <Actions actions={actions} />
                        <h1><strong>{i18n('detail.text.terms.selection')}</strong></h1>
                        <article
                            title={getSelectionText(snippet)}
                            dangerouslySetInnerHTML={{ __html: snippet }} />
                    </section>
                </li>
            )
        },
        onResolve() {
            const { sign, mentionStart, mentionEnd, snippet } = this.props;
            this.props.onResolve({ sign, mentionStart, mentionEnd, snippet });
        }
    });
    const Justification = createReactClass({
        propTypes: {
            ...sharedProps,
            sandboxStatus: PropTypes.string.isRequired,
            termMentionFor: PropTypes.string.isRequired,
            termMentionForElementId: PropTypes.string.isRequired,
            snippet: PropTypes.string.isRequired,
            resolvedToVertexId: PropTypes.string,
            resolvedToEdgeId: PropTypes.string
        },
        render() {
            const {
                snippet, onHoverTerm, onFocusElements, onOpen, onFullscreen,
                termMentionFor, termMentionForElementId,
                resolvedToEdgeId, resolvedToVertexId } = this.props;

            const actions = [
                { label: i18n('detail.text.terms.open'), title: i18n('detail.text.terms.open.tooltip'), handler: onOpen },
                { label: i18n('detail.text.terms.openfullscreen'), title: i18n('detail.text.terms.openfullscreen.tooltip'), handler: onFullscreen }
            ]
            const what = i18n(`detail.text.terms.justification.prefix.${termMentionFor === 'PROPERTY' ? 'property' : 'element'}`);
            const type = (
                termMentionFor === 'VERTEX' || (
                    termMentionFor === 'PROPERTY' && termMentionForElementId === resolvedToVertexId
                )) ? 'vertices' : 'edges';
            const element = this.props[type][termMentionForElementId];

            // Server sanitizes snippets so be dangerous
            return (
                <li className="justification" onMouseEnter={onHoverTerm}>
                    <Icon src={'../img/glyphicons_custom/justification.png'} />
                    <section>
                        <Actions actions={actions} />
                        <h1><strong>{i18n('detail.text.terms.justification')}</strong> {what} <Element element={element} onFocusElements={onFocusElements} /></h1>
                        <article
                            title={getSelectionText(snippet)}
                            dangerouslySetInnerHTML={{ __html: snippet }} />
                    </section>
                </li>
            )
        }
    })
    const Resolved = createReactClass({
        propTypes: {
            ...sharedProps,
            title: PropTypes.string.isRequired,
            termMentionFor: PropTypes.string.isRequired,
            resolvedToVertexId: PropTypes.string.isRequired,
            resolvedToEdgeId: PropTypes.string.isRequired,
            conceptType: PropTypes.string,
            process: PropTypes.string
        },
        render() {
            const {
                title,
                onOpen,
                onFullscreen,
                onUnresolve,
                onHoverTerm,
                onFocusElements,
                canEdit,
                termMentionFor,
                sandboxStatus,
                conceptType,
                getConceptOrDefault,
                resolvedToVertexId,
                vertices
            } = this.props;
            const concept = getConceptOrDefault(conceptType);
            const element = vertices[resolvedToVertexId];
            const actions = [];

            if (element) {
                actions.push(
                        { label: i18n('detail.text.terms.open'), title: i18n('detail.text.terms.open.tooltip'), handler: onOpen },
                        { label: i18n('detail.text.terms.openfullscreen'), title: i18n('detail.text.terms.openfullscreen.tooltip'), handler: onFullscreen }
                    );

                    const canUnresolve = canEdit && termMentionFor === 'VERTEX' && sandboxStatus !== 'PUBLIC';
                if (canUnresolve) {
                        actions.push({ label: i18n('detail.text.terms.unresolve'), title: i18n('detail.text.terms.unresolve.tooltip'), handler: onUnresolve, classes: { 'btn-danger': true }})
                    }
            }

            return (
                <li className="resolved" onMouseEnter={onHoverTerm}>
                    {element && !F.vertex.imageIsFromConcept(element) ? (
                        <Icon isImage src={F.vertex.image(element, null, 30)} />
                    ) : (
                        <Icon src={concept.glyphIconHref} />
                    )}
                    <section>
                            <Actions actions={actions} />
                            <h1><strong>{i18n('detail.text.terms.resolved')}</strong> {i18n('detail.text.terms.resolved.prefix')} <em>{concept.displayName}</em> <Element element={element} onFocusElements={onFocusElements} /></h1>
                        <article>
                            <span className="selection">{title}</span>
                        </article>
                    </section>
                </li>
            )
        }
    })
    const Suggestion = createReactClass({
        propTypes: {
            ...sharedProps,
            title: PropTypes.string.isRequired,
            process: PropTypes.string,
            conceptType: PropTypes.string
        },
        render() {
            const { title, process, conceptType, getConceptOrDefault, onHoverTerm, onProperty, canEdit } = this.props;
            const actions = [];
            const concept = getConceptOrDefault(conceptType);
            if (canEdit) {
                actions.push({ label: i18n('detail.text.terms.resolve'), title: i18n('detail.text.terms.resolve.tooltip'), handler: this.onResolve, classes: { 'btn-success': true } });
            }
            return (
                <li className="suggestion" onMouseEnter={onHoverTerm}>
                    <Icon src={'../img/glyphicons/glyphicons_194_circle_question_mark@2x.png'} />
                    <section>
                        <Actions actions={actions} />
                        <h1>
                            <strong>{i18n('detail.text.terms.resolvable')}</strong> <em>{concept.displayName}</em> {i18n('detail.text.terms.resolvable.prefix')} <strong>{process}</strong>
                        </h1>
                        <article>
                            <span className="selection">{title}</span>
                        </article>
                    </section>
                </li>
            )
        },
        onResolve() {
            const {
                conceptType,
                start: mentionStart,
                end: mentionEnd,
                title: sign,
                id: resolvedFromTermMention
            } = this.props;
            this.props.onResolve({
                conceptType,
                mentionStart,
                mentionEnd,
                sign,
                resolvedFromTermMention
            });
        }
    })


    const Term = createReactClass({
        propTypes: {
            term: PropTypes.oneOfType([
                PropTypes.shape(sharedProps),
                PropTypes.shape(selectionProps)
            ]).isRequired,
            privileges: PropTypes.object.isRequired,
            actions: PropTypes.object.isRequired,
            getConceptOrDefault: PropTypes.func.isRequired
        },
        render() {
            const { term, privileges, actions, getConceptOrDefault, vertices, edges, onFocusElements, ...rest } = this.props;
            const { type } = term;
            const addTermToActions = (func, name) => {
                if (name === 'onResolve') return func;
                return (event) => func(term, event);
            };
            const itemProps = {
                ...term,
                ..._.mapObject(actions, addTermToActions),
                getConceptOrDefault,
                vertices,
                edges,
                onFocusElements,
                onHoverTerm: this.onHoverTerm,
                canEdit: privileges.EDIT,
            };

            switch (type) {
                case 'resolved': return (<Resolved {...itemProps} />);
                case 'suggestion': return (<Suggestion {...itemProps} />);
                case 'justification': return (<Justification {...itemProps} />);
                case 'selection': return (<TermSelection {...itemProps} />);
            }

            return null;
        },
        onHoverTerm(event) {
            this.props.onHoverTerm(this.props.term.refId);
        }
    });

    return Term;
});

