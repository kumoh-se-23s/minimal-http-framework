package com.minihttp.core.inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerTest {
    static final class CloseLog {
        private final List<String> closedNames = new ArrayList<>();

        void record(String name) {
            this.closedNames.add(name);
        }

        List<String> closedNames() {
            return this.closedNames;
        }
    }

    interface ItemRepository {
        String find();
    }

    static final class InMemoryItemRepository implements ItemRepository, AutoCloseable {
        private final CloseLog closeLog;

        InMemoryItemRepository(CloseLog closeLog) {
            this.closeLog = closeLog;
        }

        @Override
        public String find() {
            return "item";
        }

        @Override
        public void close() {
            this.closeLog.record("repository");
        }
    }

    static final class ItemService implements AutoCloseable {
        private final ItemRepository itemRepository;
        private final CloseLog closeLog;

        ItemService(ItemRepository itemRepository, CloseLog closeLog) {
            this.itemRepository = itemRepository;
            this.closeLog = closeLog;
        }

        @Override
        public void close() {
            this.closeLog.record("itemService");
        }
    }

    static final class AuditService {
        private final ItemRepository itemRepository;

        AuditService(ItemRepository itemRepository) {
            this.itemRepository = itemRepository;
        }
    }

    static final class ItemController {
        private final ItemService itemService;
        private final AuditService auditService;

        ItemController(ItemService itemService, AuditService auditService) {
            this.itemService = itemService;
            this.auditService = auditService;
        }
    }

    static final class LombokStyleService {
        private final ItemRepository itemRepository;

        private LombokStyleService() {
            this(null);
        }

        public LombokStyleService(ItemRepository itemRepository) {
            this.itemRepository = itemRepository;
        }
    }

    record ItemView(ItemService itemService, int pageSize) {
    }

    static final class AmbiguousService {
        public AmbiguousService() {
        }

        public AmbiguousService(ItemRepository itemRepository) {
        }
    }

    static final class NeedsUnboundRepository {
        NeedsUnboundRepository(ItemRepository itemRepository) {
        }
    }

    static final class CycleA {
        CycleA(CycleB cycleB) {
        }
    }

    static final class CycleB {
        CycleB(CycleA cycleA) {
        }
    }

    static final class ExplodingService {
        ExplodingService() {
            throw new IllegalArgumentException("boom");
        }
    }

    static final class FailingClose implements AutoCloseable {
        private final CloseLog closeLog;

        FailingClose(CloseLog closeLog) {
            this.closeLog = closeLog;
        }

        @Override
        public void close() throws Exception {
            this.closeLog.record("failingClose");
            throw new Exception("close failed");
        }
    }

    static final class ProvidedResource implements AutoCloseable {
        private boolean closed = false;

        @Override
        public void close() {
            this.closed = true;
        }
    }

    static final class UsesProvidedResource {
        private final ProvidedResource providedResource;

        UsesProvidedResource(ProvidedResource providedResource) {
            this.providedResource = providedResource;
        }
    }

    private static Container containerWithRepository() {
        Container container = Container.create();
        container.provide(CloseLog.class, new CloseLog());
        container.bind(ItemRepository.class, InMemoryItemRepository.class);
        return container;
    }

    @Nested
    @DisplayName("생성")
    class CreationTest {
        @Test
        void buildsChainThroughBinding() {
            Container container = ContainerTest.containerWithRepository();

            ItemController itemController = container.get(ItemController.class);

            assertEquals("item", itemController.itemService.itemRepository.find());
            assertTrue(itemController.itemService.itemRepository instanceof InMemoryItemRepository);
        }

        @Test
        void sharesSingletonAcrossDependents() {
            Container container = ContainerTest.containerWithRepository();

            ItemController itemController = container.get(ItemController.class);

            assertSame(itemController.itemService.itemRepository, itemController.auditService.itemRepository);
            assertSame(itemController.itemService, container.get(ItemService.class));
            assertSame(container.get(ItemRepository.class), container.get(InMemoryItemRepository.class));
        }

        @Test
        void returnsProvidedInstance() {
            CloseLog closeLog = new CloseLog();
            Container container = Container.create();
            container.provide(CloseLog.class, closeLog);

            assertSame(closeLog, container.get(CloseLog.class));
        }

        @Test
        void picksSinglePublicConstructorAmongPrivateOnes() {
            Container container = ContainerTest.containerWithRepository();

            assertTrue(container.get(LombokStyleService.class).itemRepository instanceof InMemoryItemRepository);
        }

        @Test
        void buildsRecordWithProvidedPrimitive() {
            Container container = ContainerTest.containerWithRepository();
            container.provide(int.class, 20);

            ItemView itemView = container.get(ItemView.class);

            assertEquals(20, itemView.pageSize());
            assertSame(container.get(ItemService.class), itemView.itemService());
        }
    }

    @Nested
    @DisplayName("등록과 생성 실패")
    class FailureTest {
        @Test
        void rejectsAmbiguousConstructors() {
            Container container = ContainerTest.containerWithRepository();

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> container.get(AmbiguousService.class));

            assertTrue(exception.getMessage().contains("Candidates"));
        }

        @Test
        void rejectsUnboundInterfaceWithRequiringChain() {
            Container container = Container.create();

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> container.get(NeedsUnboundRepository.class));

            assertTrue(exception.getMessage().contains("No implementation for " + ItemRepository.class.getName()));
            assertTrue(exception.getMessage().contains("required by " + NeedsUnboundRepository.class.getName()));
        }

        @Test
        void reportsCycleChain() {
            Container container = Container.create();

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> container.get(CycleA.class));

            String expectedChain = CycleA.class.getName() + " → " + CycleB.class.getName() + " → " + CycleA.class.getName();
            assertEquals("Circular dependency: " + expectedChain, exception.getMessage());
        }

        @Test
        void rejectsDuplicateRegistration() {
            Container container = ContainerTest.containerWithRepository();

            assertThrows(IllegalStateException.class,
                         () -> container.bind(ItemRepository.class, InMemoryItemRepository.class));
            assertThrows(IllegalStateException.class, () -> container.provide(CloseLog.class, new CloseLog()));
        }

        @Test
        void rejectsBindingToAbstractType() {
            Container container = Container.create();

            assertThrows(IllegalArgumentException.class, () -> container.bind(ItemRepository.class, ItemRepository.class));
        }

        @Test
        void wrapsConstructorFailureWithTypeName() {
            Container container = Container.create();

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> container.get(ExplodingService.class));

            assertTrue(exception.getMessage().contains(ExplodingService.class.getName()));
            assertEquals("boom", exception.getCause().getMessage());
        }

        @Test
        void rejectsJdkValueWithoutProvide() {
            Container container = Container.create();

            assertThrows(IllegalStateException.class, () -> container.get(String.class));
        }
    }

    @Nested
    @DisplayName("종료")
    class CloseTest {
        @Test
        void closesCreatedInstancesInReverseOrder() {
            Container container = ContainerTest.containerWithRepository();
            CloseLog closeLog = container.get(CloseLog.class);
            container.get(ItemController.class);

            container.close();

            assertEquals(List.of("itemService", "repository"), closeLog.closedNames());
        }

        @Test
        void keepsClosingWhenOneFails() {
            CloseLog closeLog = new CloseLog();
            Container container = Container.create();
            container.provide(CloseLog.class, closeLog);
            container.bind(ItemRepository.class, InMemoryItemRepository.class);
            container.get(ItemRepository.class);
            container.get(FailingClose.class);

            container.close();

            assertEquals(List.of("failingClose", "repository"), closeLog.closedNames());
        }

        @Test
        void leavesProvidedInstancesOpen() {
            ProvidedResource providedResource = new ProvidedResource();
            Container container = Container.create();
            container.provide(ProvidedResource.class, providedResource);
            container.get(UsesProvidedResource.class);

            container.close();

            assertFalse(providedResource.closed);
        }

        @Test
        void closesOnlyOnce() {
            CloseLog closeLog = new CloseLog();
            Container container = Container.create();
            container.provide(CloseLog.class, closeLog);
            container.bind(ItemRepository.class, InMemoryItemRepository.class);
            container.get(ItemRepository.class);

            container.close();
            container.close();

            assertEquals(List.of("repository"), closeLog.closedNames());
            assertThrows(IllegalStateException.class, () -> container.get(ItemRepository.class));
        }
    }
}
