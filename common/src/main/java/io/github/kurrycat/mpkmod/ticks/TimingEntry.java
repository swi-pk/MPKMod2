package io.github.kurrycat.mpkmod.ticks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.kurrycat.mpkmod.util.Range;
import io.github.kurrycat.mpkmod.util.Tuple;
import io.github.kurrycat.mpkmod.util.input.InputPredicate;
import io.github.kurrycat.mpkmod.util.input.InputPredicateBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class TimingEntry {
    public final String varName;
    public final Range range;

    public final InputPredicateBase inputPredicate;
    public final Boolean P, N;
    public final GroundState G;
    /** Measure ms between the movement and jump press within the matched tick (jam). */
    public final boolean sameTickMS;

    public TimingEntry(
            String varName,
            Integer min,
            Integer max,
            InputPredicateBase inputPredicate,
            Boolean P,
            Boolean N,
            GroundState G
    ) {
        this(varName, min, max, inputPredicate, P, N, G, null);
    }

    @JsonCreator
    public TimingEntry(
            @JsonProperty("var")
            String varName,
            @JsonProperty("min")
            Integer min,
            @JsonProperty("max")
            Integer max,
            @JsonProperty("inputs")
            InputPredicateBase inputPredicate,
            @JsonProperty("sprint")
            Boolean P,
            @JsonProperty("sneak")
            Boolean N,
            @JsonProperty("groundState")
            GroundState G,
            @JsonProperty("sameTickMS")
            Boolean sameTickMS
    ) {
        if (min == null && max == null) min = max = 1;
        if (min == null || min <= 0) min = 0;
        if (max != null && max < min) max = min;

        this.varName = varName;
        this.range = new Range(min, max);
        this.inputPredicate = inputPredicate;
        this.P = P;
        this.N = N;
        this.G = G;
        this.sameTickMS = sameTickMS != null && sameTickMS;
    }

    public boolean varNameMatches(TimingEntry other) {
        return varName != null && other.varName != null && varName.equals(other.varName);
    }

    private boolean matchesInput(TimingInput timingInput) {
        return (
                (inputPredicate == null || inputPredicate.matches(timingInput.inputVector)) &&
                (P == null || P == timingInput.P) &&
                (N == null || N == timingInput.N) &&
                (G == null || G == timingInput.G)
        );
    }

    /**
     * @param inputList List of {@link TimingInput} instances
     * @param startIndex the index it should start matching from
     * @param vars HashMap containing all variables for that {@link Timing}. Vars of this TimingEntry are added
     * @param repeatedVar if the current var appeared in the previous {@link TimingEntry}
     * @return amount of matched inputs or null if no match was found (returns 0 for variable entries with 0 within range)
     */
    public Integer matches(List<TimingInput> inputList, int startIndex, HashMap<String, Timing.TickMS> vars, boolean repeatedVar) {
        int i = startIndex;

        if (inputPredicate instanceof InputPredicate) ((InputPredicate) inputPredicate).resetLastMatch();
        while (i < inputList.size() && matchesInput(inputList.get(i))) i++;
        int count = range.constrain(i - startIndex);

        if (range.isValueBelow(i - startIndex)) return null;

        if (varName != null) {
            if (vars.containsKey(varName) && repeatedVar) {
                vars.get(varName).tickCount += count;
            } else {
                vars.put(varName, new Timing.TickMS(count));
            }

            int varStart = i - vars.get(varName).tickCount;
            int varCount = vars.get(varName).tickCount;
            vars.get(varName).ms = sameTickMS
                    ? getSameTickMS(varStart, varCount, inputList)
                    : getMS(varStart, varCount, inputList);
        }

        return count;
    }

    private Integer getMS(int startIndex, int matchCount, List<TimingInput> inputList) {
        // if at least one tick matches and a tick after match exists
        if (matchCount == 0 || startIndex + matchCount >= inputList.size())
            return null;

        TimingInput before = startIndex == 0 ? TimingInput.stopTick() : inputList.get(startIndex - 1);
        TimingInput after = inputList.get(startIndex + matchCount);
        ArrayList<TimingInput> curr = new ArrayList<>();
        for (int i = startIndex; i < startIndex + matchCount; i++) {
            if (curr.isEmpty() || !curr.get(curr.size() - 1).equals(inputList.get(i))) {
                curr.add(inputList.get(i));
            }
        }

        Tuple<ButtonMS.Button, ButtonMS.Button> range = TimingInput.findMSButtons(before, after, curr);
        if (range == null)
            return null;

        ButtonMS startMS = curr.get(0).msList.forKey(range.getFirst());
        if (startMS == null) return null;
        ButtonMS endMS = after.msList.forKey(range.getSecond());
        if (endMS == null) return null;
        return endMS.msFrom(startMS);
    }

    /** Absolute ms between the last movement press and the jump press of a single tick, or null. */
    private static Integer getSameTickMS(int startIndex, int matchCount, List<TimingInput> inputList) {
        if (matchCount != 1 || startIndex < 0 || startIndex >= inputList.size())
            return null;

        ButtonMSList msList = inputList.get(startIndex).msList;
        ButtonMS jump = msList.forKey(ButtonMS.Button.JUMP, true);
        if (jump == null) return null;

        ButtonMS move = null;
        for (ButtonMS buttonMS : msList) {
            if (buttonMS.isState(true) && buttonMS.getButton().isMovement())
                move = buttonMS;
        }
        if (move == null) return null;

        return Math.abs(jump.msFrom(move));
    }

    public TimingEntry mirrored() {
        InputPredicateBase mirroredInputPredicate = inputPredicate;
        if (inputPredicate instanceof InputPredicate)
            mirroredInputPredicate = ((InputPredicate) inputPredicate).mirrored();

        return new TimingEntry(varName, range.getLower(), range.getUpper(), mirroredInputPredicate, P, N, G, sameTickMS);
    }
}
