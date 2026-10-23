package moe.ono.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class MobileQQServiceResolverTest {
    public static class Service {}
    public static class DerivedService extends Service {}
    public static class OldRuntime {
        private final Service service = new Service();
    }
    public static class ChildRuntime extends OldRuntime {}
    public static class NewRuntime {
        private Service current;
        NewRuntime(Service current) { this.current = current; }
        public Service getMobileQQService() { return current; }
    }
    public static class InheritedAccessor extends NewRuntime {
        InheritedAccessor(Service current) { super(current); }
    }
    public static class CovariantRuntime {
        public DerivedService getMobileQQService() { return new DerivedService(); }
    }
    public static class NullFieldRuntime extends NewRuntime {
        private final Service unused = null;
        NullFieldRuntime(Service current) { super(current); }
    }
    public static class FailedGetterRuntime extends OldRuntime {
        public Service getMobileQQService() { throw new IllegalStateException("not ready"); }
    }
    public static class StaticRuntime {
        private static final Service service = new Service();
        public static Service getMobileQQService() { return service; }
    }

    @Test public void oldQqKeepsFieldLookup() {
        OldRuntime runtime = new OldRuntime();
        assertSame(runtime.service, MobileQQServiceResolver.resolve(runtime, Service.class));
    }

    @Test public void movedFieldCanBeInherited() {
        ChildRuntime runtime = new ChildRuntime();
        assertSame(((OldRuntime) runtime).service, MobileQQServiceResolver.resolve(runtime, Service.class));
    }

    @Test public void qq9375UsesPublicGetter() {
        Service service = new Service();
        assertSame(service, MobileQQServiceResolver.resolve(new NewRuntime(service), Service.class));
    }

    @Test public void inheritedGetterIsSupported() {
        Service service = new Service();
        assertSame(service, MobileQQServiceResolver.resolve(new InheritedAccessor(service), Service.class));
    }

    @Test public void covariantGetterIsSupported() {
        assertTrue(MobileQQServiceResolver.resolve(new CovariantRuntime(), Service.class) instanceof DerivedService);
    }

    @Test public void nullFieldDoesNotHideLiveGetter() {
        Service service = new Service();
        assertSame(service, MobileQQServiceResolver.resolve(new NullFieldRuntime(service), Service.class));
    }

    @Test public void rebindingDoesNotReuseOldAccount() {
        Service first = new Service();
        Service second = new Service();
        NewRuntime runtime = new NewRuntime(first);
        assertSame(first, MobileQQServiceResolver.resolve(runtime, Service.class));
        runtime.current = second;
        assertSame(second, MobileQQServiceResolver.resolve(runtime, Service.class));
        assertSame(first, MobileQQServiceResolver.resolve(new NewRuntime(first), Service.class));
    }

    @Test public void failedGetterCanFallBackToLiveField() {
        FailedGetterRuntime runtime = new FailedGetterRuntime();
        assertSame(((OldRuntime) runtime).service, MobileQQServiceResolver.resolve(runtime, Service.class));
    }

    @Test public void nullServiceReportsFailure() {
        assertThrows(IllegalStateException.class,
                () -> MobileQQServiceResolver.resolve(new NewRuntime(null), Service.class));
    }

    @Test public void staticMembersAreNotAccountServices() {
        assertThrows(IllegalStateException.class,
                () -> MobileQQServiceResolver.resolve(new StaticRuntime(), Service.class));
    }
}
