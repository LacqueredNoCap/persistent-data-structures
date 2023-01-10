package com.github.object.persistence.sql.impl;

import com.github.object.persistence.api.session.Session;
import com.github.object.persistence.api.session.SessionFactory;
import com.github.object.persistence.common.ConfigDataSource;
import com.github.object.persistence.common.ConnectionInstaller;
import com.github.object.persistence.common.DataSourceWrapper;
import org.atteo.classindex.ClassIndex;

import javax.persistence.Entity;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class SqlFactoryImpl implements SessionFactory {

    private final ConnectionInstaller<Connection> installer;

    public SqlFactoryImpl(ConnectionInstaller<Connection> installer) {
        this.installer = installer;
        initializeDatasource();
    }

    @Override
    public Session openSession() {
        DataSourceWrapper<Connection> wrapper = installer.installConnection();
        FromSqlToObjectMapper<Connection> mapper = new FromSqlToObjectMapper<>(SqlGenerator.getInstance());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        return new SqlSession(wrapper, mapper, executor);
    }

    @Override
    public void initializeDatasource() {
        if (ConfigDataSource.getInstance().isInitializeNeeded()) {
            Iterable<Class<?>> entityClasses = ClassIndex.getAnnotated(Entity.class);
            installer.installConnection().execute(validateAndCreateTables(entityClasses));
        }
    }

    private String validateAndCreateTables(Iterable<Class<?>> entityClasses) {
        return StreamSupport
                .stream(entityClasses.spliterator(), false)
                .map(kClass -> SqlGenerator.getInstance().createTable(kClass))
                .collect(Collectors.joining(" "));
    }
}
