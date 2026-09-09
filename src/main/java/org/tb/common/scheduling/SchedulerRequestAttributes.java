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
        throw new UnsupportedOperationException(name + "#" + callback + "#" + scope);
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
