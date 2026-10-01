package dev.tigermce.assassins;

import dev.tigermce.assassins.model.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrackerTest {
    private final UUID hunter = UUID.randomUUID(), targetId = UUID.randomUUID();
    private final ItemStack[] slots = new ItemStack[41];
    private final Player player = mock(Player.class), target = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final AssassinsPlugin plugin = mock(AssassinsPlugin.class);
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ItemStack> construction;
    private EventManager events;
    private TrackerListener listener;

    @BeforeEach void setup() {
        bukkit = mockStatic(Bukkit.class);
        when(plugin.getName()).thenReturn("Assassins");
        when(plugin.namespace()).thenReturn("assassins");
        when(player.getUniqueId()).thenReturn(hunter);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(slots.length);
        when(inventory.getContents()).thenAnswer(i -> Arrays.stream(slots).map(s -> s == null ? null : s.clone()).toArray(ItemStack[]::new));
        when(inventory.getItem(anyInt())).thenAnswer(i -> slots[i.getArgument(0)] == null ? null : slots[(int) i.getArgument(0)].clone());
        doAnswer(i -> { ItemStack stack = i.getArgument(1); slots[i.getArgument(0)] = stack == null ? null : stack.clone(); return null; }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        when(inventory.firstEmpty()).thenAnswer(i -> { for (int n = 0; n < 36; n++) if (slots[n] == null) return n; return -1; });
        when(inventory.addItem(any(ItemStack[].class))).thenAnswer(i -> {
            ItemStack[] inserted = (ItemStack[]) i.getRawArguments()[0];
            HashMap<Integer, ItemStack> leftovers = new HashMap<>();
            for (int n = 0; n < inserted.length; n++) {
                int slot = inventory.firstEmpty();
                if (slot < 0) leftovers.put(n, inserted[n].clone()); else slots[slot] = inserted[n].clone();
            }
            return leftovers;
        });
        when(target.isOnline()).thenReturn(true);
        when(target.getLocation()).thenReturn(new Location(mock(World.class), 1, 64, 2));
        OfflinePlayer named = mock(OfflinePlayer.class);
        when(named.getName()).thenReturn("TargetPlayer");
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);
        bukkit.when(() -> Bukkit.getOfflinePlayer(targetId)).thenReturn(named);
        EventState state = new EventState();
        state.active = true; state.rounds = 2; state.bossBar = false;
        state.participants.add(hunter);
        state.assignments.add(Map.of(hunter, targetId));
        state.progress.put(hunter, new PlayerProgress(0, Long.MAX_VALUE));
        events = new EventManager(plugin, mock(Persistence.class), new Persistence.Loaded(new DraftConfig(), state, new HashMap<>()));
        listener = new TrackerListener(plugin, events);
        construction = mockConstruction(ItemStack.class, (stack, context) -> configureStack(stack, new MetaState(), 1));
    }

    @AfterEach void close() {
        if (construction != null) construction.close();
        if (bukkit != null) bukkit.close();
        META_STATES.clear();
    }

    @Test void repeatedTicksInsertOneFullyConfiguredTrackerAndUpdateCopiedInventory() {
        bukkit.when(() -> Bukkit.getPlayer(hunter)).thenReturn(player);
        for (int i = 0; i < 10; i++) events.tick();
        assertEquals(1, trackerCount());
        assertEquals(1, slots[0].getAmount());
        CompassMeta meta = (CompassMeta) slots[0].getItemMeta();
        assertEquals(Boolean.TRUE, meta.getEnchantmentGlintOverride());
        assertEquals(Component.text("Target › ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("TargetPlayer", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)), meta.displayName());
        assertEquals(target.getLocation(), meta.getLodestone());
        Location moved = new Location(mock(World.class), 8, 70, 9);
        when(target.getLocation()).thenReturn(moved);
        events.tick();
        assertEquals(moved, ((CompassMeta) slots[0].getItemMeta()).getLodestone());
        verify(inventory, never()).addItem(any(ItemStack[].class));
    }

    @Test void offhandTrackerUpdatesWithoutNewIssuance() {
        events.updateTracker(player);
        slots[40] = slots[0]; slots[0] = null;
        events.updateTracker(player);
        assertEquals(1, trackerCount()); assertNull(slots[0]); assertNotNull(slots[40]);
    }

    @Test void duplicateStacksCollapseAndOrdinaryCompassesAreUntouched() {
        events.updateTracker(player);
        slots[0].setAmount(32); slots[5] = slots[0].clone(); slots[40] = slots[0].clone();
        ItemStack ordinary = mock(ItemStack.class);
        when(ordinary.getType()).thenReturn(Material.COMPASS); when(ordinary.clone()).thenReturn(ordinary);
        slots[6] = ordinary;
        events.updateTracker(player);
        assertEquals(1, trackerCount()); assertEquals(1, slots[0].getAmount());
        assertSame(ordinary, slots[6]); assertNull(slots[5]); assertNull(slots[40]);
    }

    @Test void fullInventoryRetriesWithoutOverwritingItems() {
        ItemStack ordinary = mock(ItemStack.class); when(ordinary.clone()).thenReturn(ordinary);
        Arrays.fill(slots, ordinary);
        events.updateTracker(player);
        assertEquals(0, trackerCount());
        slots[7] = null; events.updateTracker(player);
        assertEquals(1, trackerCount()); assertTrue(events.isTracker(slots[7])); assertSame(ordinary, slots[6]);
    }

    @Test void targetChangeUpdatesNameAndOfflineTargetClearsDestination() {
        events.updateTracker(player);
        UUID next = UUID.randomUUID(); OfflinePlayer named = mock(OfflinePlayer.class);
        when(named.getName()).thenReturn("NextPlayer");
        bukkit.when(() -> Bukkit.getOfflinePlayer(next)).thenReturn(named);
        events.state.assignments.add(Map.of(hunter, next)); events.state.progress(hunter).round++;
        events.updateTracker(player);
        CompassMeta meta = (CompassMeta) slots[0].getItemMeta();
        assertEquals("NextPlayer", ((net.kyori.adventure.text.TextComponent) meta.displayName().children().getFirst()).content());
        assertNull(meta.getLodestone()); assertEquals(1, trackerCount());
        assertEquals(List.of(dev.tigermce.assassins.util.Items.text("Target is offline", NamedTextColor.GRAY),
                dev.tigermce.assassins.util.Items.text("Round 2/2", NamedTextColor.YELLOW)), meta.lore());
    }

    @Test void deadPlayersReceiveNoTrackerAndFinishedOrStoppedPlayersLoseIt() {
        when(player.isDead()).thenReturn(true); events.updateTracker(player); assertEquals(0, trackerCount());
        when(player.isDead()).thenReturn(false); events.updateTracker(player);
        events.state.progress(hunter).finished = true; events.updateTracker(player); assertEquals(0, trackerCount());
        events.state.progress(hunter).finished = false; events.updateTracker(player);
        events.state.active = false; events.updateTracker(player); assertEquals(0, trackerCount());
    }

    @Test void manualDropsBlockOnlyTaggedTrackersDuringEvent() {
        events.updateTracker(player);
        for (int amount : new int[] {1, 64}) {
            Item item = mock(Item.class); ItemStack tracker = slots[0].clone(); tracker.setAmount(amount);
            when(item.getItemStack()).thenReturn(tracker);
            PlayerDropItemEvent drop = new PlayerDropItemEvent(player, item); listener.onDrop(drop); assertTrue(drop.isCancelled());
            events.state.active = false;
            drop = new PlayerDropItemEvent(player, item); listener.onDrop(drop); assertFalse(drop.isCancelled());
            events.state.active = true;
        }
        Item ordinary = mock(Item.class); when(ordinary.getItemStack()).thenReturn(mock(ItemStack.class));
        PlayerDropItemEvent drop = new PlayerDropItemEvent(player, ordinary); listener.onDrop(drop); assertFalse(drop.isCancelled());
    }

    @Test void deathRetainsOneTrackerAndRespawnRefreshesWithoutDuplication() {
        for (boolean keepInventory : new boolean[] {false, true}) {
            Arrays.fill(slots, null); events.updateTracker(player); slots[0].setAmount(16); slots[40] = slots[0].clone();
            ItemStack ordinary = mock(ItemStack.class);
            List<ItemStack> drops = new ArrayList<>(List.of(slots[0].clone(), slots[40].clone(), ordinary));
            List<ItemStack> kept = new ArrayList<>(List.of(slots[0].clone(), ordinary));
            PlayerDeathEvent death = mock(PlayerDeathEvent.class);
            when(death.getPlayer()).thenReturn(player); when(death.getKeepInventory()).thenReturn(keepInventory);
            when(death.getDrops()).thenReturn(drops); when(death.getItemsToKeep()).thenReturn(kept);
            listener.onDeath(death);
            assertEquals(List.of(ordinary), drops);
            assertEquals(keepInventory ? 0 : 1, kept.stream().filter(events::isTracker).count());
            if (!keepInventory) { Arrays.fill(slots, null); slots[0] = kept.stream().filter(events::isTracker).findFirst().orElseThrow().clone(); }
            BukkitScheduler scheduler = mock(BukkitScheduler.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(i -> { ((Runnable) i.getArgument(1)).run(); return null; }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
            PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class); when(respawn.getPlayer()).thenReturn(player);
            listener.onRespawn(respawn);
            assertEquals(1, trackerCount()); assertEquals(1, slots[0].getAmount());
        }
    }

    @Test void eventEndingBeforeRespawnRemovesRetainedTracker() {
        events.updateTracker(player); events.state.active = false;
        events.updateTracker(player); assertEquals(0, trackerCount());
    }

    @Test void finishedPlayerJoiningLosesStaleTracker() {
        events.updateTracker(player);
        events.state.progress(hunter).finished = true;
        events.join(player);
        assertEquals(0, trackerCount());
    }

    @Test void stoppedEventDoesNotRetainTrackersOnDeath() {
        events.updateTracker(player);
        List<ItemStack> drops = new ArrayList<>(List.of(slots[0].clone()));
        List<ItemStack> kept = new ArrayList<>(List.of(slots[0].clone()));
        events.state.active = false;
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getPlayer()).thenReturn(player);
        when(death.getDrops()).thenReturn(drops); when(death.getItemsToKeep()).thenReturn(kept);
        listener.onDeath(death);
        assertTrue(drops.isEmpty()); assertTrue(kept.isEmpty()); assertEquals(0, trackerCount());
    }

    private long trackerCount() { return Arrays.stream(slots).filter(events::isTracker).count(); }

    // The inventory copies stacks on both reads and writes, as a real Bukkit inventory can.
    private static void configureStack(ItemStack stack, MetaState initial, int amount) {
        MetaState[] state = {initial}; int[] count = {amount};
        when(stack.getType()).thenReturn(Material.COMPASS);
        when(stack.hasItemMeta()).thenAnswer(i -> state[0].tagged);
        when(stack.getItemMeta()).thenAnswer(i -> state[0].meta());
        doAnswer(i -> { state[0] = META_STATES.get(i.getArgument(0)).copy(); return true; }).when(stack).setItemMeta(any());
        when(stack.getAmount()).thenAnswer(i -> count[0]);
        doAnswer(i -> { count[0] = i.getArgument(0); return null; }).when(stack).setAmount(anyInt());
        when(stack.clone()).thenAnswer(i -> { ItemStack copy = mock(ItemStack.class); configureStack(copy, state[0].copy(), count[0]); return copy; });
    }

    private static final Map<CompassMeta, MetaState> META_STATES = new IdentityHashMap<>();
    private static class MetaState {
        boolean tagged; Boolean glint; Component name; List<Component> lore; Location destination;
        MetaState copy() { MetaState copy = new MetaState(); copy.tagged = tagged; copy.glint = glint; copy.name = name; copy.lore = lore; copy.destination = destination; return copy; }
        CompassMeta meta() {
            MetaState copy = copy(); CompassMeta meta = mock(CompassMeta.class); META_STATES.put(meta, copy);
            PersistentDataContainer data = mock(PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(data);
            when(data.has(any(NamespacedKey.class), any())).thenAnswer(i -> copy.tagged);
            doAnswer(i -> { copy.tagged = true; return null; }).when(data).set(any(NamespacedKey.class), any(), any());
            when(meta.displayName()).thenAnswer(i -> copy.name);
            doAnswer(i -> { copy.name = i.getArgument(0); return null; }).when(meta).displayName(any(Component.class));
            when(meta.lore()).thenAnswer(i -> copy.lore);
            doAnswer(i -> { copy.lore = i.getArgument(0); return null; }).when(meta).lore(anyList());
            when(meta.getEnchantmentGlintOverride()).thenAnswer(i -> copy.glint);
            doAnswer(i -> { copy.glint = i.getArgument(0); return null; }).when(meta).setEnchantmentGlintOverride(anyBoolean());
            when(meta.getLodestone()).thenAnswer(i -> copy.destination);
            doAnswer(i -> { copy.destination = i.getArgument(0); return null; }).when(meta).setLodestone(nullable(Location.class));
            return meta;
        }
    }
}
