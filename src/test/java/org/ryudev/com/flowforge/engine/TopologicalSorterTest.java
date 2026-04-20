package org.ryudev.com.flowforge.engine;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ryudev.com.flowforge.domain.*;
import org.ryudev.com.flowforge.exception.DagValidationException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TopologicalSorter Unit Tests")
class TopologicalSorterTest {

    private TopologicalSorter sorter;

    @BeforeEach
    void setUp() {
        sorter = new TopologicalSorter();
    }

    // ── Happy Path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Single step DAG returns one level with that step")
    void singleStep_returnsOneLevel() {
        DagDefinition dag = dag(
                List.of(step("A")),
                List.of()
        );

        List<Set<String>> levels = sorter.sort(dag);

        assertThat(levels).hasSize(1);
        assertThat(levels.get(0)).containsExactly("A");
    }

    @Test
    @DisplayName("Linear chain A→B→C returns three sequential levels")
    void linearChain_returnsSequentialLevels() {
        DagDefinition dag = dag(
                List.of(step("A"), step("B"), step("C")),
                List.of(edge("A", "B"), edge("B", "C"))
        );

        List<Set<String>> levels = sorter.sort(dag);

        assertThat(levels).hasSize(3);
        assertThat(levels.get(0)).containsExactly("A");
        assertThat(levels.get(1)).containsExactly("B");
        assertThat(levels.get(2)).containsExactly("C");
    }

    @Test
    @DisplayName("Parallel steps (fan-out) are grouped in same level")
    void fanOut_groupsParallelStepsInSameLevel() {
        // A → B, A → C, A → D  (B, C, D are parallel)
        DagDefinition dag = dag(
                List.of(step("A"), step("B"), step("C"), step("D")),
                List.of(edge("A", "B"), edge("A", "C"), edge("A", "D"))
        );

        List<Set<String>> levels = sorter.sort(dag);

        assertThat(levels).hasSize(2);
        assertThat(levels.get(0)).containsExactly("A");
        assertThat(levels.get(1)).containsExactlyInAnyOrder("B", "C", "D");
    }

    @Test
    @DisplayName("Fan-in (join): B and C finish before D starts")
    void fanIn_synchronizesBeforeNextStep() {
        // B → D, C → D (D waits for both B and C)
        DagDefinition dag = dag(
                List.of(step("A"), step("B"), step("C"), step("D")),
                List.of(edge("A", "B"), edge("A", "C"), edge("B", "D"), edge("C", "D"))
        );

        List<Set<String>> levels = sorter.sort(dag);

        assertThat(levels).hasSize(3);
        assertThat(levels.get(0)).containsExactly("A");
        assertThat(levels.get(1)).containsExactlyInAnyOrder("B", "C");
        assertThat(levels.get(2)).containsExactly("D");
    }

    @Test
    @DisplayName("Diamond shape DAG resolves correctly")
    void diamondShape_resolvesCorrectly() {
        //     A
        //    / \
        //   B   C
        //    \ /
        //     D
        DagDefinition dag = dag(
                List.of(step("A"), step("B"), step("C"), step("D")),
                List.of(edge("A", "B"), edge("A", "C"), edge("B", "D"), edge("C", "D"))
        );

        List<Set<String>> levels = sorter.sort(dag);

        assertThat(levels).hasSize(3);
        assertThat(levels.get(0)).containsExactly("A");
        assertThat(levels.get(1)).containsExactlyInAnyOrder("B", "C");
        assertThat(levels.get(2)).containsExactly("D");
    }

    @Test
    @DisplayName("Disconnected nodes (no edges) all appear in level 0")
    void disconnectedNodes_allInLevelZero() {
        DagDefinition dag = dag(
                List.of(step("X"), step("Y"), step("Z")),
                List.of()
        );

        List<Set<String>> levels = sorter.sort(dag);

        assertThat(levels).hasSize(1);
        assertThat(levels.get(0)).containsExactlyInAnyOrder("X", "Y", "Z");
    }

    @Test
    @DisplayName("Complex multi-level DAG with 6 nodes")
    void complexDag_6nodes() {
        // A→C, B→C, C→D, C→E, D→F, E→F
        DagDefinition dag = dag(
                List.of(step("A"), step("B"), step("C"), step("D"), step("E"), step("F")),
                List.of(
                        edge("A", "C"), edge("B", "C"),
                        edge("C", "D"), edge("C", "E"),
                        edge("D", "F"), edge("E", "F")
                )
        );

        List<Set<String>> levels = sorter.sort(dag);

        assertThat(levels).hasSize(4);
        assertThat(levels.get(0)).containsExactlyInAnyOrder("A", "B");
        assertThat(levels.get(1)).containsExactly("C");
        assertThat(levels.get(2)).containsExactlyInAnyOrder("D", "E");
        assertThat(levels.get(3)).containsExactly("F");
    }

    // ── Cycle Detection ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Direct self-loop is detected as cycle")
    void selfLoop_throwsDagValidationException() {
        DagDefinition dag = dag(
                List.of(step("A")),
                List.of(edge("A", "A"))
        );

        assertThatThrownBy(() -> sorter.sort(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("Self-loop");
    }

    @Test
    @DisplayName("Two-node cycle A→B→A is detected")
    void twoNodeCycle_throwsDagValidationException() {
        DagDefinition dag = dag(
                List.of(step("A"), step("B")),
                List.of(edge("A", "B"), edge("B", "A"))
        );

        assertThatThrownBy(() -> sorter.sort(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("Cycle detected");
    }

    @Test
    @DisplayName("Three-node cycle A→B→C→A is detected")
    void threeNodeCycle_throwsDagValidationException() {
        DagDefinition dag = dag(
                List.of(step("A"), step("B"), step("C")),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "A"))
        );

        assertThatThrownBy(() -> sorter.sort(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("Cycle detected");
    }

    // ── Validation Errors ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Null DAG throws DagValidationException")
    void nullDag_throwsException() {
        assertThatThrownBy(() -> sorter.sort(null))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("Empty steps list throws DagValidationException")
    void emptySteps_throwsException() {
        DagDefinition dag = dag(List.of(), List.of());

        assertThatThrownBy(() -> sorter.sort(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("at least one step");
    }

    @Test
    @DisplayName("Duplicate step IDs throw DagValidationException")
    void duplicateStepId_throwsException() {
        DagDefinition dag = dag(
                List.of(step("A"), step("A")),
                List.of()
        );

        assertThatThrownBy(() -> sorter.validate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("Duplicate step ID");
    }

    @Test
    @DisplayName("Edge referencing unknown step throws DagValidationException")
    void edgeWithUnknownStep_throwsException() {
        DagDefinition dag = dag(
                List.of(step("A")),
                List.of(edge("A", "NONEXISTENT"))
        );

        assertThatThrownBy(() -> sorter.validate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("unknown step");
    }

    @ParameterizedTest
    @DisplayName("Blank step ID throws DagValidationException")
    @ValueSource(strings = {"", " ", "\t"})
    void blankStepId_throwsException(String id) {
        StepDefinition badStep = StepDefinition.builder()
                .id(id)
                .name("Test")
                .type(StepType.DELAY)
                .build();

        DagDefinition dag = dag(List.of(badStep), List.of());

        assertThatThrownBy(() -> sorter.validate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("Step ID cannot be null or blank");
    }

    @Test
    @DisplayName("Null step type throws DagValidationException")
    void nullStepType_throwsException() {
        StepDefinition badStep = StepDefinition.builder()
                .id("A")
                .name("Test")
                .type(null)
                .build();

        DagDefinition dag = dag(List.of(badStep), List.of());

        assertThatThrownBy(() -> sorter.validate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("type cannot be null");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DagDefinition dag(List<StepDefinition> steps, List<EdgeDefinition> edges) {
        return DagDefinition.builder().steps(steps).edges(edges).build();
    }

    private StepDefinition step(String id) {
        return StepDefinition.builder()
                .id(id)
                .name("Step " + id)
                .type(StepType.DELAY)
                .config(Map.of("durationMs", 100))
                .build();
    }

    private EdgeDefinition edge(String from, String to) {
        return EdgeDefinition.builder().from(from).to(to).build();
    }
}