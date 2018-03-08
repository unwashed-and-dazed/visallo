define([
    'create-react-class',
    'util/formatters'
], function (createReactClass, F) {
    'use strict';

    return createReactClass({
        render() {
            const user = visalloData.currentUser;
            const privileges = _.sortBy(user.privileges.map(p => ({
                name: p,
                title: i18n(`useraccount.page.access.privileges.${p}`),
                desc: i18n(`useraccount.page.access.privileges.${p}.description`)
            })), 'title');
            const authorizations = user.authorizations || [];

            return (<div className="access">
                <h1>{i18n('useraccount.page.access.previousLogin')}</h1>
                <p>
                    {user.previousLoginDate ? F.date.dateTimeString(user.previousLoginDate) : i18n('useraccount.page.access.firstLogin')}
                </p>

                <h1>{i18n('useraccount.page.access.authorizations')}</h1>
                <p>
                    {authorizations.length > 0 ? authorizations.join(', ') : (<i>{i18n('useraccount.modal.access.auths.none')}</i>)}
                </p>

                <h1>{i18n('useraccount.page.access.privileges')}</h1>
                <ul>
                    {privileges.map(({name, title, desc}) => (
                        <li key={name}>
                            <h1>{title}</h1>
                            <p>{desc}</p>
                        </li>
                    ))}
                </ul>
            </div>);
        }
    })
});
