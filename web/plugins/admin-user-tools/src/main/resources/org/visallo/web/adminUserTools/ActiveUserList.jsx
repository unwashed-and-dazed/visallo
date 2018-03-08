define([
    'create-react-class',
    'util/formatters'
], function (createReactClass, F) {

    const ActiveUserList = createReactClass({
        dataRequest: null,

        getInitialState() {
            return {
                users: [],
                loading: true,
                error: null
            };
        },

        componentDidMount() {
            this.dataRequest = this.props.visalloApi.v1.dataRequest;
            this.loadUsers();
        },

        handleRefreshClick() {
            this.loadUsers();
        },

        loadUsers() {
            this.setState({
                users: [],
                loading: true,
                error: null
            });
            this.dataRequest('user', 'search', {
                online: 'true'
            })
                .then(users => {
                    this.setState({
                        loading: false,
                        error: null,
                        users: users
                    });
                })
                .catch(err => {
                    console.error('Could not get online users', err);
                    this.setState({
                        loading: false,
                        error: err
                    });
                });
        },

        render() {
            if (this.state.loading) {
                return (<div className="loading-container">{i18n('admin.user.activeList.loading')}</div>);
            }
            if (this.state.error) {
                return (<div className="error">{i18n('admin.user.activeList.error')}</div>);
            }

            return (<section className="collapsible has-badge-number expanded active-user-list">
                <h1 className="collapsible-header">
                    <strong>{i18n('admin.user.activeList.userList')}</strong>
                    <s className="refresh"
                       title={i18n('admin.user.activeList.refresh')}
                       onClick={this.handleRefreshClick}/>
                    <span className="badge">{F.number.pretty(this.state.users.length)}</span>
                </h1>
                <div>
                    <ul className="nav-list nav">
                        {this.state.users.map(user => {
                            const {
                                id, userName, displayName,
                                sessionCount, currentLoginDate,
                                currentWorkspaceName, currentWorkspaceId
                            } = user;

                            return (<li className="highlight-on-hover" key={id}>
                                <span className="nav-list-title">{userName}</span>
                                <ul className="inner-list">
                                    <label className="nav-header">
                                        {i18n('admin.user.activeList.userId')}
                                        <span>{id}</span>
                                    </label>
                                    <label className="nav-header">
                                        {i18n('admin.user.activeList.displayName')}
                                        <span>{displayName}</span>
                                    </label>
                                    <label className="nav-header">
                                        {i18n('admin.user.activeList.loginDate')}
                                        <span>{F.date.dateTimeString(currentLoginDate)}</span>
                                    </label>
                                    <label className="nav-header">
                                        {i18n('admin.user.activeList.currentWorkspace')}
                                        <span>{currentWorkspaceName} ({currentWorkspaceId})</span>
                                    </label>
                                </ul>
                            </li>);
                        })}
                    </ul>
                </div>
            </section>);
        }
    });

    return ActiveUserList;
});

