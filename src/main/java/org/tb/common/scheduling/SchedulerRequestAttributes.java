package org.tb.common.scheduling;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.AbstractRequestAttributes;

/**
 * A stand-in request scope for scheduled jobs.
 *
 * <p>Request-scoped beans — {@code AuthorizedUser} above all — do not exist outside a request, so a
 * job that needs one has to open a scope of its own. A scheduler binds an instance of this to the
 * thread, initialises the user for job mode, runs, and unbinds again.
 *
 * <p>It lives in {@code common} because it says nothing about any domain: reporting and budget both
 * schedule jobs and both need it. It started out as a nested class of the report scheduler, which
 * made the budget module import {@code reporting} for a purely technical utility — the one import
 * that stood between {@code budget} and a dependency list free of surprises (#918).
 *
 * <p>Only the methods a scheduled job actually reaches are implemented; the rest fail loudly rather
 * than pretending to be a real request.
 *
 * <p><b>Nothing is destroyed by hand when a job ends.</b> A scheduler unbinds with
 * {@code resetRequestAttributes()}, and that drops this whole object together with the beans inside
 * it — the cleanup is complete at that point. A {@code beanFactory.destroyScopedBean("authorizedUser")}
 * stood next to it in all five schedulers and never once succeeded:
 * {@code @RequestScope(proxyMode = TARGET_CLASS)} registers <em>two</em> definitions — the singleton
 * {@code ScopedProxyFactoryBean} under {@code authorizedUser} and the request-scoped bean under
 * {@code scopedTarget.authorizedUser} — and {@code AbstractBeanFactory.destroyScopedBean} rejects
 * the first as not corresponding "to an object in a mutable scope". The call threw on every run and
 * a {@code catch (Exception ignored)} swallowed it, along with any real failure in the cleanup.
 * Do not add it back (#1084).
 *
 * <p><b>Known limitation: a request-scoped bean reached by a job must not have a destruction
 * method.</b> {@link #registerDestructionCallback} throws, and Spring calls it while <em>creating</em>
 * a bean that needs destroying — so a {@code @PreDestroy}, a {@code DisposableBean} or a named
 * {@code destroyMethod} on {@code AuthorizedUser} or {@code UiState} would take down every scheduled
 * job and every ETL run at once, at {@code getObject()} rather than at cleanup. Neither bean has one
 * today. Keeping the callbacks instead would mean implementing
 * {@link #updateAccessedSessionAttributes()} as well ({@code AbstractRequestAttributes#requestCompleted}
 * calls it) and giving every scheduler a {@code requestCompleted()} call — machinery for a case that
 * does not exist, and the quiet alternative is the worse one: a destruction method that is never run
 * and nobody notices. If a bean in a job path ever gets one, this class is what changes, not the job.
 */
public class SchedulerRequestAttributes extends AbstractRequestAttributes {

    private final Map<Integer, Map<String, Object>> attributes = new HashMap<>();
    private final String mockSessionId = UUID.randomUUID().toString();
    private final Object mutex = this;

    @Override
    protected void updateAccessedSessionAttributes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable Object getAttribute(String name, int scope) {
        return attributes.computeIfAbsent(scope, k -> new HashMap<>()).get(name);
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {
        attributes.computeIfAbsent(scope, k -> new HashMap<>()).put(name, value);
    }

    @Override
    public void removeAttribute(String name, int scope) {
        attributes.computeIfAbsent(scope, k -> new HashMap<>()).remove(name);
    }

    @Override
    public String[] getAttributeNames(int scope) {
        return attributes.computeIfAbsent(scope, k -> new HashMap<>()).keySet().toArray(new String[0]);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback, int scope) {
        // Reached while the bean is being created, not while it is being cleaned up - see the
        // class javadoc on why this stays a refusal rather than becoming a stored callback.
        throw new UnsupportedOperationException(
            "Bean '" + name + "' (scope " + scope + ") has a destruction method, which a job scope "
                + "does not run. Either drop the destruction method or teach "
                + "SchedulerRequestAttributes to keep the callbacks - see its javadoc (#1084).");
    }

    @Override
    public @Nullable Object resolveReference(String key) {
        throw new UnsupportedOperationException(key);
    }

    @Override
    public String getSessionId() {
        return mockSessionId;
    }

    @Override
    public Object getSessionMutex() {
        return mutex;
    }

}
