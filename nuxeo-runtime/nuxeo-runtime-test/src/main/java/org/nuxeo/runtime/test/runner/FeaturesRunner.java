/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.runtime.test.runner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;
import org.nuxeo.common.Environment;
import org.nuxeo.common.LoaderConstants;
import org.nuxeo.osgi.bootstrap.OSGiClassLoader;
import org.nuxeo.runtime.test.TargetResourceLocator;
import org.nuxeo.runtime.test.runner.FeaturesLoader.Callable;
import org.nuxeo.runtime.test.runner.FeaturesLoader.Direction;
import org.nuxeo.runtime.test.runner.FeaturesLoader.Holder;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.name.Names;

/**
 * A Test Case runner that can be extended through features and provide injection though Guice.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@SuppressWarnings("deprecation")
public class FeaturesRunner extends BlockJUnit4ClassRunner {

    protected final Properties properties;

    protected final AnnotationScanner scanner;

    protected Class<?> otherClass;

    /**
     * Guice injector.
     */
    protected Injector injector;

    protected final FeaturesLoader loader = new FeaturesLoader(this);

    protected final TargetResourceLocator locator;

    public FeaturesRunner(Class<?> classToRun) throws InitializationError {
        this(classToRun, new Properties(System.getProperties()));
    }

    public FeaturesRunner(Class<?> classToRun, Properties properties) throws InitializationError {
        super(classToRun);
        locator = new TargetResourceLocator(classToRun);
        scanner = new AnnotationScanner();
        this.properties = properties;
    }

    public Properties getProperties() {
        return properties;
    }

    @Override
    public void run(final RunNotifier notifier) {
        Description description = getDescription();
        if (!(this.getClass().getClassLoader() instanceof OSGiClassLoader)) {
            try {
                new OSGiTestLoader(description.getDisplayName()).reloadAndRun(this, notifier);
            } catch (Exception cause) {
                notifier.fireTestFailure(new Failure(description, cause));
            }
            return;
        }
        Environment.getDefault().setProperty(LoaderConstants.APP_NAME, description.getClassName());
        EachTestNotifier testNotifier = new EachTestNotifier(notifier, description);
        try {
            if (otherClass != null) {
                loader.loadFeatures(otherClass);
            }
            loader.loadFeatures(getTargetTestClass());
        } catch (Exception cause) {
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            testNotifier.addFailure(cause);
            return;
        }
        injector = onInjector(notifier);
        try {
            classBlock(notifier).evaluate();
        } catch (AssumptionViolatedException e) {
            testNotifier.fireTestIgnored();
        } catch (StoppedByUserException e) {
            throw e;
        } catch (Throwable e) {
            testNotifier.addFailure(e);
        } finally {
            onResetInjector(notifier);
        }
        return;
    }

    public Class<?> getTargetTestClass() {
        return super.getTestClass().getJavaClass();
    }

    public Path getTargetTestBasepath() {
        return locator.getBasepath();
    }

    public URL getTargetTestResource(String name) {
        return locator.getTargetTestResource(name);
    }

    public Iterable<RunnerFeature> getFeatures() {
        return loader.features();
    }

    /**
     * @since 5.6
     */
    public <T extends Annotation> T getConfig(Class<T> type) {
        List<T> configs = new ArrayList<>();
        // fetch config on target test class
        {
            T annotation = scanner.getAnnotation(getTargetTestClass(), type);
            if (annotation != null) {
                configs.add(annotation);
            }
        }
        // fetch config on feature holders
        loader.apply(Direction.BACKWARD, new Callable() {
            @Override
            public void call(Holder holder) throws Exception {
                T annotation = scanner.getAnnotation(holder.type, type);
                if (annotation != null) {
                    configs.add(annotation);
                }
            }
        });
        return Defaults.of(type, configs);
    }

    /**
     * Get the annotation on the test method, if no annotation has been found, get the annotation from the test class
     * (See {@link #getConfig(Class)})
     *
     * @since 5.7
     */
    public <T extends Annotation> T getConfig(FrameworkMethod method, Class<T> type) {
        T config = method.getAnnotation(type);
        if (config != null) {
            return config;
        }
        // if not define, try to get the config of the class
        return getConfig(type);

    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        List<FrameworkMethod> methods = super.computeTestMethods();
        // sort a copy
        methods = new ArrayList<>(methods);
        MethodSorter.sortMethodsUsingSourceOrder(methods);
        return methods;
    }

    protected void initialize() throws Exception {
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.initialize(FeaturesRunner.this);
            }

        });
    }

    protected void beforeRun() throws Exception {
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.beforeRun(FeaturesRunner.this);
            }

            @Override
            public void rollback(Holder holder) throws Exception {
                holder.feature.afterRun(FeaturesRunner.this);
            }
        });
    }

    protected void beforeMethodRun(final FrameworkMethod method, final Object test) throws Exception {
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.beforeMethodRun(FeaturesRunner.this, method, test);
            }
        });
    }

    protected void afterMethodRun(final FrameworkMethod method, final Object test) throws Exception {
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.afterMethodRun(FeaturesRunner.this, method, test);
            }
        });
    }

    protected void afterRun() throws Exception {
        loader.apply(Direction.BACKWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.afterRun(FeaturesRunner.this);
            }
        });
    }

    protected void start() throws Exception {
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.start(FeaturesRunner.this);
            }

            @Override
            public void rollback(Holder holder) throws Exception {
                holder.feature.stop(FeaturesRunner.this);
            }
        });
    }

    protected void stop() throws Exception {
        loader.apply(Direction.BACKWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.stop(FeaturesRunner.this);
            }
        });
    }

    protected void beforeSetup() throws Exception {

        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.beforeSetup(FeaturesRunner.this);
            }

        });

        injector.injectMembers(underTest);
    }

    protected void afterTeardown() {
        loader.apply(Direction.BACKWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.afterTeardown(FeaturesRunner.this);
            }

        });
    }

    public Injector getInjector() {
        return injector;
    }

    protected Injector onInjector(final RunNotifier aNotifier) {
        return Guice.createInjector(Stage.DEVELOPMENT, new Module() {

            @Override
            public void configure(Binder aBinder) {
                aBinder.bind(FeaturesRunner.class).toInstance(FeaturesRunner.this);
                aBinder.bind(Properties.class).toInstance(properties);
                aBinder.bind(RunNotifier.class).toInstance(aNotifier);
                aBinder.bind(TargetResourceLocator.class).toInstance(locator);
            }

        });
    }

    protected void onResetInjector(RunNotifier notifier) {
        try {
            Field field = injector.getClass().getDeclaredField("localContext");
            field.setAccessible(true);
            ThreadLocal<?> instance = (ThreadLocal<?>) field.get(injector);
            instance.remove();
        } catch (ReflectiveOperationException cause) {
            notifier.fireTestFailure(
                    new Failure(getDescription(), new AssertionError("Cannot reset injector thread local", cause)));
        } finally {
            injector = null;
        }
    }

    protected class BeforeClassStatement extends Statement {
        protected final Statement next;

        protected BeforeClassStatement(Statement aStatement) {
            next = aStatement;
        }

        @Override
        public void evaluate() throws Throwable {
            initialize();
            start();
            injector = injector.createChildInjector(loader.onModule());
            try {
                beforeRun();
                next.evaluate();
            } finally {
                injector = injector.getParent();
            }
        }

    }

    protected class AfterClassStatement extends Statement {
        protected final Statement previous;

        protected AfterClassStatement(Statement aStatement) {
            previous = aStatement;
        }

        @Override
        public void evaluate() throws Throwable {
            previous.evaluate();
            try {
                afterRun();
            } finally {
                stop();
            }
        }
    }

    @Override
    protected Statement withAfterClasses(Statement statement) {
        Statement actual = statement;
        actual = super.withAfterClasses(actual);
        actual = new AfterClassStatement(actual);
        return actual;
    }

    class ChildrenInvoker extends Statement {

        Statement base;

        public ChildrenInvoker(Statement base) {
            this.base = base;
        }

        @Override
        public void evaluate() throws Throwable {
            base.evaluate();
        }

    }

    @Override
    protected List<TestRule> classRules() {
        final RulesFactory<ClassRule, TestRule> factory = new RulesFactory<>(ClassRule.class, TestRule.class);

        factory.withRule(new TestRule() {
            @Override
            public Statement apply(Statement base, Description description) {
                return new BeforeClassStatement(base);
            }
        }).withRules(super.classRules());
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                factory.withRules(holder.testClass, null);
            }
        });

        return factory.build();
    }

    protected class BeforeMethodRunStatement extends Statement {

        protected final Statement next;

        protected final FrameworkMethod method;

        protected final Object target;

        protected BeforeMethodRunStatement(FrameworkMethod aMethod, Object aTarget, Statement aStatement) {
            method = aMethod;
            target = aTarget;
            next = aStatement;
        }

        @Override
        public void evaluate() throws Throwable {
            beforeMethodRun(method, target);
            next.evaluate();
        }

    }

    protected class BeforeSetupStatement extends Statement {

        protected final Statement next;

        protected BeforeSetupStatement(Statement aStatement) {
            next = aStatement;
        }

        @Override
        public void evaluate() throws Throwable {
            beforeSetup();
            next.evaluate();
        }

    }

    @Override
    protected Statement withBefores(FrameworkMethod method, Object target, Statement statement) {
        Statement actual = statement;
        actual = new BeforeMethodRunStatement(method, target, actual);
        actual = super.withBefores(method, target, actual);
        actual = new BeforeSetupStatement(actual);
        return actual;
    }

    protected class AfterMethodRunStatement extends Statement {

        protected final Statement previous;

        protected final FrameworkMethod method;

        protected final Object target;

        protected AfterMethodRunStatement(FrameworkMethod aMethod, Object aTarget, Statement aStatement) {
            method = aMethod;
            target = aTarget;
            previous = aStatement;
        }

        @Override
        public void evaluate() throws Throwable {
            try {
                previous.evaluate();
            } finally {
                afterMethodRun(method, target);
            }
        }

    }

    protected class AfterTeardownStatement extends Statement {

        protected final Statement previous;

        protected AfterTeardownStatement(Statement aStatement) {
            previous = aStatement;
        }

        @Override
        public void evaluate() throws Throwable {
            try {
                previous.evaluate();
            } finally {
                afterTeardown();
            }
        }

    }

    @Override
    protected Statement withAfters(FrameworkMethod method, Object target, Statement statement) {
        Statement actual = statement;
        actual = new AfterMethodRunStatement(method, target, actual);
        actual = super.withAfters(method, target, actual);
        actual = new AfterTeardownStatement(actual);
        return actual;
    }

    @Override
    protected List<TestRule> getTestRules(Object target) {
        final RulesFactory<Rule, TestRule> factory = new RulesFactory<>(Rule.class, TestRule.class);
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                factory.withRules(holder.testClass, holder.feature);
            }

        });
        factory.withRules(getTestClass(), target);
        return factory.build();
    }

    @Override
    protected List<MethodRule> rules(Object target) {
        final RulesFactory<Rule, MethodRule> factory = new RulesFactory<>(Rule.class, MethodRule.class);
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                factory.withRules(holder.testClass, holder.feature);
            }

        });
        factory.withRules(getTestClass(), target);
        return factory.build();
    }

    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        final Statement actual = super.methodInvoker(method, test);
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                injector.injectMembers(underTest);
                actual.evaluate();
            }

        };
    }

    protected Object underTest;

    @Override
    public Object createTest() throws Exception {
        underTest = super.createTest();
        loader.apply(Direction.FORWARD, new Callable() {

            @Override
            public void call(Holder holder) throws Exception {
                holder.feature.testCreated(underTest);
            }

        });
        // TODO replace underTest member with a binding
        // Class<?> testType = underTest.getClass();
        // injector.getInstance(Binder.class).bind(testType)
        // .toInstance(testType.cast(underTest));
        return underTest;
    }

    @Override
    protected void validateZeroArgConstructor(List<Throwable> errors) {
        // Guice can inject constructors with parameters so we don't want this
        // method to trigger an error
    }

    @Override
    public String toString() {
        return "FeaturesRunner [fTest=" + getTargetTestClass() + "]";
    }

    protected class RulesFactory<A extends Annotation, R> {

        protected Statement build(final Statement base, final String name) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    injector = injector.createChildInjector(new Module() {

                        @SuppressWarnings({ "unchecked", "rawtypes" })
                        @Override
                        public void configure(Binder binder) {
                            for (Object each : rules) {
                                binder.bind((Class) each.getClass()).annotatedWith(Names.named(name)).toInstance(each);
                                binder.requestInjection(each);
                            }
                        }

                    });

                    try {
                        base.evaluate();
                    } finally {
                        injector = injector.getParent();
                    }

                }

            };

        }

        protected class BindRule implements TestRule, MethodRule {

            @Override
            public Statement apply(Statement base, FrameworkMethod method, Object target) {
                Statement statement = build(base, "method");
                for (Object each : rules) {
                    statement = ((MethodRule) each).apply(statement, method, target);
                }
                return statement;
            }

            @Override
            public Statement apply(Statement base, Description description) {
                if (rules.isEmpty()) {
                    return base;
                }
                Statement statement = build(base, "test");
                for (Object each : rules) {
                    statement = ((TestRule) each).apply(statement, description);
                }
                return statement;
            }

        }

        protected final Class<A> annotationType;

        protected final Class<R> ruleType;

        protected ArrayList<R> rules = new ArrayList<>();

        protected RulesFactory(Class<A> anAnnotationType, Class<R> aRuleType) {
            annotationType = anAnnotationType;
            ruleType = aRuleType;
        }

        public RulesFactory<A, R> withRules(List<R> someRules) {
            for (R eachRule : someRules) {
                withRule(eachRule);
            }
            return this;
        }

        public RulesFactory<A, R> withRule(R aRule) {
            injector.injectMembers(aRule);
            rules.add(aRule);
            return this;
        }

        public RulesFactory<A, R> withRules(TestClass aType, Object aTest) {
            for (R each : aType.getAnnotatedFieldValues(aTest, annotationType, ruleType)) {
                withRule(each);
            }

            for (FrameworkMethod each : aType.getAnnotatedMethods(annotationType)) {
                if (ruleType.isAssignableFrom(each.getMethod().getReturnType())) {
                    withRule(onMethod(ruleType, each, aTest));
                }
            }
            return this;
        }

        public List<R> build() {
            return Collections.singletonList(ruleType.cast(new BindRule()));
        }

        protected R onMethod(Class<R> aRuleType, FrameworkMethod aMethod, Object aTarget, Object... someParms) {
            try {
                return aRuleType.cast(aMethod.invokeExplosively(aTarget, someParms));
            } catch (Throwable cause) {
                throw new RuntimeException("Errors in rules factory " + aMethod, cause);
            }
        }

    }

    public <T extends RunnerFeature> T getFeature(Class<T> aType) {
        return loader.getFeature(aType);
    }

    public AnnotationScanner getScanner() {
        return scanner;
    }

}
