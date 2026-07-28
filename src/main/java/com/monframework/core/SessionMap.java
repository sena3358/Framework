package com.monframework.core;

import jakarta.servlet.http.HttpSession;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wrapper Map autour de HttpSession.
 */
public class SessionMap extends AbstractMap<String, Object> {
    private final HttpSession session;

    public SessionMap(HttpSession session) {
        this.session = session;
    }

    @Override
    public Object get(Object key) {
        return session.getAttribute(String.valueOf(key));
    }

    @Override
    public Object put(String key, Object value) {
        Object previous = session.getAttribute(key);
        session.setAttribute(key, value);
        return previous;
    }

    @Override
    public Object remove(Object key) {
        String attributeName = String.valueOf(key);
        Object previous = session.getAttribute(attributeName);
        session.removeAttribute(attributeName);
        return previous;
    }

    @Override
    public void clear() {
        for (String name : attributeNames()) {
            session.removeAttribute(name);
        }
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> entries = new HashSet<>();
        for (String name : attributeNames()) {
            entries.add(new SimpleEntry<>(name, session.getAttribute(name)));
        }
        return entries;
    }

    @Override
    public int size() {
        return attributeNames().size();
    }

    private List<String> attributeNames() {
        Collection<String> names = java.util.Collections.list(session.getAttributeNames());
        return new ArrayList<>(names);
    }
}