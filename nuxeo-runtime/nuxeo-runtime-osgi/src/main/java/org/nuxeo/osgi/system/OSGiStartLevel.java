package org.nuxeo.osgi.system;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.nuxeo.common.trycompanion.Try;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;

public class OSGiStartLevel {

    protected final OSGiSystem system;

    protected final Map<Bundle, Activation> byBundles = new HashMap<>();

    protected final TreeSet<Activation> activations = new TreeSet<>(new Comparator<Activation>() {

        @Override
        public int compare(Activation o1, Activation o2) {
            int delta = o1.level - o2.level;
            if (delta != 0) {
                return delta;
            }
            return Long.valueOf(o1.id - o2.id).intValue();
        }

    });

    public OSGiStartLevel(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiStartLevel>() {

            @Override
            public Class<OSGiStartLevel> typeof() {
                return OSGiStartLevel.class;
            }

            @Override
            public OSGiStartLevel adapt(Bundle bundle) {
                return OSGiStartLevel.this;
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<Activation>() {

            @Override
            public Class<Activation> typeof() {
                return Activation.class;
            }

            @Override
            public Activation adapt(Bundle bundle) {
                if (!byBundles.containsKey(bundle)) {
                    return new Activation(initial, bundle);
                }
                return byBundles.get(bundle);
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<FrameworkStartLevel>() {

            @Override
            public Class<FrameworkStartLevel> typeof() {
                return FrameworkStartLevel.class;
            }

            @Override
            public FrameworkStartLevel adapt(Bundle bundle) {
                return new FrameworkStartLevel() {

                    @Override
                    public Bundle getBundle() {
                        return system;
                    }

                    @Override
                    public int getStartLevel() {
                        return active;
                    }

                    @Override
                    public void setStartLevel(final int to, FrameworkListener... listeners) {
                        if (active == to) {
                            return;
                        }
                        system.adapt(OSGiEventRelayer.Activation.class).add(FrameworkListener.class, listeners);
                        int from = active;
                        TryCompanion.<Void>of(BundleException.class)
                                .successOf()
                                .sneakyConsume(self -> { // @formatter:off
                  if (active <= to) {
                     incStartLevel(from, to, self);
                   } else {
                     decStartLevel(from, to, self);
                   }
                 }) // @formatter:on
                                .peek(self -> active = to)
                                .onFailure(self -> { // @formatter:off
                  BundleException errors = new BundleException(
                    "caught error while updating start level from " + active + " to " + to);
                  self.failed().stream().forEach(cause -> errors.addSuppressed(cause));
                  system.adapt(FrameworkListener.class)
                      .frameworkEvent(
                          new FrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED | FrameworkEvent.ERROR,
                          system.bundle,
                          errors));
                 }) // @formatter:on
                                .onSuccess(self -> { // @formatter:off
                  system.adapt(FrameworkListener.class)
                      .frameworkEvent(
                          new FrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED,
                          system.bundle,
                          null));
                 }) // @formatter:on
                                .peek(
                                        self -> system.adapt(OSGiEventRelayer.Activation.class)
                                                .remove(FrameworkListener.class, listeners));
                    }

                    @Override
                    public int getInitialBundleStartLevel() {
                        return initial;
                    }

                    @Override
                    public void setInitialBundleStartLevel(int to) {
                        initial = to;
                    }

                };

            }

        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<BundleStartLevel>() {

            @Override
            public Class<BundleStartLevel> typeof() {
                return BundleStartLevel.class;
            }

            @Override
            public BundleStartLevel adapt(Bundle bundle) {
                return new BundleStartLevel() {

                    Activation activation = bundle.adapt(OSGiStartLevel.Activation.class);

                    @Override
                    public Bundle getBundle() {
                        return bundle;
                    }

                    @Override
                    public void setStartLevel(int startlevel) {
                        activation = updateTo(activation, startlevel);
                    }

                    @Override
                    public boolean isPersistentlyStarted() {
                        return false;
                    }

                    @Override
                    public boolean isActivationPolicyUsed() {
                        return false;
                    }

                    @Override
                    public int getStartLevel() {
                        return activation.level;
                    }
                };
            }
        });
    }

    int initial = 1;

    int active = -1;

    class Activation {
        final int level;

        final Bundle bundle;

        final long id;

        Activation(int level, Bundle bundle) {
            this.bundle = bundle;
            id = bundle.getBundleId();
            this.level = level;
        }

        Activation(int level, long id) {
            this.level = level;
            this.id = id;
            bundle = null;
        }

        public void start() throws BundleException {
            if (active <= level) {
                return;
            }
            bundle.start();
        }

        public void stop() throws BundleException {
            if (active <= level) {
                return;
            }
            bundle.stop();
        }

        public void install() {
            if (bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null) {
                return;
            }
            activations.add(this);
            byBundles.put(bundle, this);
            FrameworkEvent event = new FrameworkEvent(FrameworkEvent.INFO, bundle, null);
            if (active < 0) {
                return;
            }
            if (level > active) {
                try {
                    bundle.stop();
                } catch (BundleException cause) {
                    event = new FrameworkEvent(FrameworkEvent.ERROR, bundle, cause);
                }
            } else if (level <= active) {
                try {
                    bundle.start();
                } catch (BundleException cause) {
                    event = new FrameworkEvent(FrameworkEvent.ERROR, bundle, cause);
                }
            }
            system.adapt(FrameworkListener.class).frameworkEvent(event);
        }

        public void uninstall() {
            byBundles.remove(bundle);
            activations.remove(this);
        }

        String internalToString() {
            return "level=" + level;
        }

        @Override
        public String toString() {
            return "StartLevel[" + bundle + ",level=" + level + "]";
        }
    }

    protected Activation updateTo(Activation activation, int to) {
        // update index
        activation.uninstall();
        activation = new Activation(to, activation.bundle);
        activation.install();
        return activation;
    }

    protected void incStartLevel(int from, int to, Try<Void> context) {
        Activation begin = new Activation(0, 0L);
        Activation end = new Activation(to, system.registry.last);
        NavigableSet<Activation> selection = activations.subSet(begin, true, end, true);
        context
                .sneakyForEachAndCollect(selection.stream(),
                        activation -> activation.bundle.start(Bundle.START_ACTIVATION_POLICY));
    }

    protected Throwable searchBundleException(Throwable cause) {
        do {
            if (cause instanceof BundleException) {
                return cause;
            }
            cause = cause.getCause();
        } while (cause != null && cause != cause.getCause());
        return cause;
    }

    protected void decStartLevel(int from, int to, Try<Void> context) {
        Activation start = new Activation(to + 1, 0L);
        Activation end = new Activation(Integer.MAX_VALUE, system.registry.last);
        NavigableSet<Activation> selection = activations.subSet(start, true, end, true).descendingSet();
        context.sneakyForEachAndCollect(selection.stream(), activation -> activation.bundle.stop())
                .sneakyRun(() -> {
                    if (to >= 0) {
                        incStartLevel(-1, to, context);
                    }
                });
    }

}
