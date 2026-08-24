package com.android.wm.shell.triplesplit.split.util;

import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_100_33_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_100_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_100;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_33_66;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_50_50;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_66_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_33_66_33_2;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_50_50_33;
import static com.android.wm.shell.triplesplit.split.SplitScreenConstants.SNAP_TO_3_66_33_33;

import android.content.res.Resources;
import android.graphics.Rect;
import android.util.Pair;

import com.android.wm.shell.triplesplit.R;
import com.android.wm.shell.triplesplit.split.SplitScreenConstants;
import com.android.wm.shell.triplesplit.split.SplitSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * calculate the snap targets and the snap position given a position and a velocity.
 */
public class DividerSnapAlgorithm {

    private static final int MIN_FLING_VELOCITY_DP_PER_SECOND = 400;
    private static final int MIN_DISMISS_VELOCITY_DP_PER_SECOND = 600;
    public static final int SNAP_ONLY_1_1 = 1;
    public static final int SNAP_MODE_FIXED_RATIO = 2;
    public static final int SNAP_FLEXIBLE_HYBRID = 3;

    private final float mMinFlingVelocityPxPerSecond;
    private final float mMinDismissVelocityPxPerSecond;
    private final int mDisplayWidth;
    private final int mDisplayHeight;
    private final int mDividerSize;
    private final ArrayList<Pair<SnapTarget, SnapTarget>> mTargets = new ArrayList<>();
    private final Rect mInsets = new Rect();
    private final Rect mPinnedTaskbarInsets = new Rect();
    private final int mSnapMode;
    private final boolean mFreeSnapMode;
    private final int mMinimalSizeResizableTask;
    private final int mTaskHeightInMinimizedMode;
    private final float mFixedRatio;
    /** Allows split ratios to calculated dynamically instead of using {@link #mFixedRatio}. */
    private final boolean mCalculateRatiosBasedOnAvailableSpace;
    /** Allows split ratios that go offscreen (a.k.a. "flexible split") */
    private final boolean mAllowOffscreenRatios;

    /**
     * Three stages equally share the screen 33% - 33% - 33%
     */
    private final Pair<SnapTarget, SnapTarget> mMiddleTarget;
    /**
     * 33 - 33 - 100
     */
    private final Pair<SnapTarget, SnapTarget> mDismissStartTarget;
    /**
     * 33 - 33 - 66
     */
    private final Pair<SnapTarget, SnapTarget> mFirstSplitTarget;
    /**
     * 100 - 33 - 33
     */
    private final Pair<SnapTarget, SnapTarget> mDismissEndTarget;
    /**
     * 66 - 33 - 33
     */
    private final Pair<SnapTarget, SnapTarget> mLastSplitTarget;
    /**
     * 33 - 33 - 33
     */
    private final Pair<SnapTarget, SnapTarget> mFirstAllSplitTarget;
    /**
     * 33 - 33 - 33
     */
    private final Pair<SnapTarget, SnapTarget> mLastAllSplitTarget;
    /**
     * 33 - 100 - 33
     */
    private final Pair<SnapTarget, SnapTarget> mMiddleOnlyTarget;

    public DividerSnapAlgorithm(Resources resources, int displayWidth, int displayHeight,
                                int dividerSize, Rect insets, int displayId) {
        this(resources, displayWidth, displayHeight, dividerSize, insets, displayId, false, true);
    }

    public DividerSnapAlgorithm(Resources resources, int displayWidth, int displayHeight,
                                int dividerSize, Rect insets, int displayId, boolean isMinimizedMode,
                                boolean isHomeResizable) {
        mMinFlingVelocityPxPerSecond = MIN_FLING_VELOCITY_DP_PER_SECOND * resources.getDisplayMetrics()
                .density;
        mMinDismissVelocityPxPerSecond = MIN_DISMISS_VELOCITY_DP_PER_SECOND * resources.getDisplayMetrics()
                .density;
        mDividerSize = dividerSize;
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        mInsets.set(insets);
        mSnapMode = SNAP_FLEXIBLE_HYBRID;

        mFreeSnapMode = true;
        mFixedRatio = resources.getFraction(R.fraction.docked_stack_divider_fixed_ratio, 1, 1);
        mMinimalSizeResizableTask = resources.getDimensionPixelSize(
                R.dimen.default_minimal_size_resizable_task);
        mCalculateRatiosBasedOnAvailableSpace = true;
        mAllowOffscreenRatios = true;
        mTaskHeightInMinimizedMode = isHomeResizable ? resources.getDimensionPixelSize(
                R.dimen.task_height_of_minimized_mode) : 0;
        calculateTargets();
        mDismissStartTarget = requireTarget(SNAP_TO_3_33_33_100);
        mFirstSplitTarget = requireTarget(SNAP_TO_3_33_33_66);
        mMiddleOnlyTarget = requireTarget(SNAP_TO_3_33_100_33);
        mMiddleTarget = requireTarget(SNAP_TO_3_33_33_33);
        mFirstAllSplitTarget = requireTarget(SNAP_TO_3_33_33_33);
        mLastAllSplitTarget = requireTarget(SNAP_TO_3_33_33_33);
        mLastSplitTarget = requireTarget(SNAP_TO_3_66_33_33);
        mDismissEndTarget = requireTarget(SNAP_TO_3_100_33_33);
    }

//    public SnapTarget calculateSnapTarget(int position, float velocity, boolean hardToDismiss) {
//        if (position < mFirstSplitTarget.position && velocity < -mMinDismissVelocityPxPerSecond) {
//            return mDismissStartTarget;
//        }
//
//    }

    /**
     * Add all pre-defined snapTargets into @link #mTargets
     * Assume in a hybrid flexible situation just like in @see #SplitScreenConstants
     */
    public void calculateTargets() {
        List<Integer> splitSpecs = SplitSpec.getSnapTargetLayout(SNAP_FLEXIBLE_HYBRID);
        List<List<Integer>> targets = spec2Positions(splitSpecs);
        for (int i = 0; i < splitSpecs.size(); i++) {
            Pair<SnapTarget, SnapTarget> targetPair = new Pair<>(
                    new SnapTarget(targets.get(i).get(0), splitSpecs.get(i), true),
                    new SnapTarget(targets.get(i).get(1), splitSpecs.get(i), true)
            );
            mTargets.add(targetPair);
        }
    }

    public Pair<SnapTarget, SnapTarget> findSnapTarget(
            @SplitScreenConstants.SnapPosition int snapPosition) {
        for (Pair<SnapTarget, SnapTarget> t: mTargets) {
            if (t.first.snapPosition == snapPosition) {
                return t;
            }
        }

        return  null;
    }

    private Pair<SnapTarget, SnapTarget> requireTarget(
            @SplitScreenConstants.SnapPosition int snapPosition) {
        final Pair<SnapTarget, SnapTarget> target = findSnapTarget(snapPosition);
        if (target == null) {
            throw new IllegalStateException("Missing snap target " + snapPosition);
        }
        return target;
    }

    public Pair<SnapTarget, SnapTarget> getFirstSplitTarget() {
        return mFirstSplitTarget;
    }

    public Pair<SnapTarget, SnapTarget> getFirstAllSplitTarget() {
        return mFirstAllSplitTarget;
    }

    public Pair<SnapTarget, SnapTarget> getDismissStartTarget() {
        return mDismissStartTarget;
    }

    public Pair<SnapTarget, SnapTarget> getMiddleTarget() {
        return mMiddleTarget;
    }

    public Pair<SnapTarget, SnapTarget> getDismissEndTarget() {
        return mDismissEndTarget;
    }

    public Pair<SnapTarget, SnapTarget> getLastSplitTarget() {
        return mLastSplitTarget;
    }

    public Pair<SnapTarget, SnapTarget> getMiddleOnlyTarget() {
        return mMiddleOnlyTarget;
    }

    public Pair<SnapTarget, SnapTarget> getLastAllSplitTarget() {
        return mLastAllSplitTarget;
    }

    /**
     * Given left/right divider position, returns Closet SnapTarget despite the other divider.
     */
    private Pair<SnapTarget, SnapTarget> snap(int index, int position, boolean hardDismiss) {
//        return new SnapTarget(position, SNAP_TO_NONE, index == 1);
        int minIndex = -1;
        float minDistance = Integer.MAX_VALUE;
        int size = mTargets.size();
        for (int i = 0; i < size; i++) {
            Pair<SnapTarget, SnapTarget> target = mTargets.get(i);
            if (index == 1) {
                float distance = Math.abs(position - target.first.position);
                if (hardDismiss) {
                    distance /= target.first.distanceMultiplier;
                }
                if (distance < minDistance) {
                    minIndex = i;
                    minDistance = distance;
                }
            } else {
                float distance = Math.abs(position - target.second.position);
                if (hardDismiss) {
                    distance /= target.second.distanceMultiplier;
                }
                if (distance < minDistance) {
                    minIndex = i;
                    minDistance = distance;
                }
            }
        }
        return mTargets.get(minIndex);
    }

    /**
     * Given left and right divider position, returns the best SnapTarget pair.
     */
    private Pair<SnapTarget, SnapTarget> snapBoth(int leftPos, int rightPos, boolean hardDismiss) {
        Pair<SnapTarget, SnapTarget> bestPair = null;
        double bestScore = Double.MAX_VALUE;

        for (Pair<SnapTarget, SnapTarget> candidate : mTargets) {
            SnapTarget leftTarget = candidate.first;
            SnapTarget rightTarget = candidate.second;

            int targetLeftPos = leftTarget.position;
            int targetRightPos = rightTarget.position;

            double leftDist = Math.abs((double) leftPos - targetLeftPos);
            double rightDist = Math.abs((double) rightPos - targetRightPos);

            if (hardDismiss) {
                leftDist /= leftTarget.distanceMultiplier;
                rightDist /= rightTarget.distanceMultiplier;
            }

            // 综合评分：左右距离之和
            double score = leftDist + rightDist;

            // 更均衡, 惩罚不均衡的情况
            score += Math.abs(leftDist - rightDist) * 0.1;

            if (score < bestScore) {
                bestScore = score;
                bestPair = candidate;
            } else if (score == bestScore) {
                // 平局策略（可选）：选取原始未缩放距离更小的那个
                double rawThis = Math.abs(leftPos - targetLeftPos) +
                        Math.abs(rightPos - targetRightPos);
                if (bestPair != null) {
                    double rawBest = Math.abs(leftPos - bestPair.first.position) +
                            Math.abs(rightPos - bestPair.second.position);
                    if (rawThis < rawBest) {
                        bestPair = candidate;
                    }
                }
            }
        }

        return bestPair;
    }

    private int getStartInset() {
        return mInsets.left;
    }

    private int getEndInset() {
        return mInsets.right;
    }

    /**
     * Given current left/right divider position, returns the closet SnapTarget on the left side.
     */
    private Pair<SnapTarget, SnapTarget> snapToPrev(int index, int position) {
        for (int i = mTargets.size() - 1; i >= 0; i--) {
            Pair<SnapTarget, SnapTarget> target = mTargets.get(i);
            if (index == 1) {
                if (target.first.position < position) {
                    return target;
                }
            } else {
                if (target.second.position < position) {
                    return target;
                }
            }
        }

        return mDismissStartTarget;
    }

    /**
     * Given current left/right divider position, returns the closet SnapTarget on the right side.
     */
    private Pair<SnapTarget, SnapTarget> snapToNext(int index, int position) {
        for (int i = 0; i < mTargets.size(); i++) {
            Pair<SnapTarget, SnapTarget> target = mTargets.get(i);
            if (index == 1) {
                if (target.first.position > position) {
                    return target;
                }
            } else {
                if (target.second.position > position) {
                    return target;
                }
            }
        }
        return mDismissEndTarget;
    }

    /**
     *
     * @param leftPos position of left divider
     * @param rightPos position of right divider
     * @param velocity current dragging velocity
     * @param hardDismiss make it a bit harder to get reach the dismiss targets
     */
    public Pair<SnapTarget, SnapTarget> calculateSnapTarget(int leftPos, int rightPos,
                                        int movingDivider, float velocity, boolean hardDismiss) {
        if (movingDivider == 1) {
            //33 33 33
            if (leftPos < mMiddleTarget.first.position &&
                    velocity < -mMinDismissVelocityPxPerSecond) {
                return mFirstSplitTarget;
            }
            if (leftPos > mMiddleTarget.second.position &&
                    velocity > mMinDismissVelocityPxPerSecond) {
                return mDismissEndTarget;
            }
            if (Math.abs(velocity) < mMinFlingVelocityPxPerSecond) {
                return snapBoth(leftPos, rightPos, hardDismiss);
            }

            if (velocity < 0) {
                return snapToPrev(movingDivider, leftPos);
            } else {
                return snapToNext(movingDivider, leftPos);
            }
        } else {
            if (rightPos < mMiddleTarget.first.position &&
                    velocity < -mMinDismissVelocityPxPerSecond) {
                return mDismissStartTarget;
            }
            if (rightPos > mMiddleTarget.second.position &&
                    velocity > mMinDismissVelocityPxPerSecond) {
                return mLastAllSplitTarget;
            }
            if (Math.abs(velocity) < mMinFlingVelocityPxPerSecond) {
                return snapBoth(leftPos, rightPos, hardDismiss);
            }

            if (velocity < 0) {
                return snapToPrev(movingDivider, rightPos);
            } else {
                return snapToNext(movingDivider, rightPos);
            }
        }
    }

    public Pair<SnapTarget, SnapTarget> getAdjustedTargets(int id, int to,
                                         int leftDivider, int rightDivider) {
        Pair<SnapTarget, SnapTarget> best = null;
        if (id == 1) {
            best = snapBoth(to, rightDivider, true);
        } else {
            best = snapBoth(leftDivider, to, true);
        }

        return best;
    }

    public Pair<SnapTarget, SnapTarget> getAdjustedTargets(int leftTo, int rightTo,
                                                           boolean hardDismiss) {
        return snapBoth(leftTo, rightTo, hardDismiss);
    }

    public @SplitScreenConstants.SnapPosition int calculateSnapPosition(int leftDivider,
                                                                        int rightDivider) {
        return snapBoth(leftDivider, rightDivider, false).first.snapPosition;
    }

    /**
     * Given left and right snap position, calculate best SnapTargets despite dismiss targets
     * , i.e. all three bounds on the screen.
     */
    public Pair<SnapTarget, SnapTarget> calculateNonDismissSnapTarget(int leftSnap, int rightSnap) {
        Pair<SnapTarget, SnapTarget> bestPair = null;
        double bestScore = Double.MAX_VALUE;

        for (Pair<SnapTarget, SnapTarget> candidate : mTargets) {
            if (!SplitSpec.THREE_TARGETS_ONSCREEN.contains(candidate.first.snapPosition)) {
                continue;
            }
            SnapTarget leftTarget = candidate.first;
            SnapTarget rightTarget = candidate.second;

            int targetLeftPos = leftTarget.position;
            int targetRightPos = rightTarget.position;

            double leftDist = Math.abs((double) leftSnap - targetLeftPos);
            double rightDist = Math.abs((double) rightSnap - targetRightPos);

            // 综合评分：左右距离之和
            double score = leftDist + rightDist;

            // 更均衡, 惩罚不均衡的情况
            score += Math.abs(leftDist - rightDist) * 0.1;

            if (score < bestScore) {
                bestScore = score;
                bestPair = candidate;
            } else if (score == bestScore) {
                // 平局策略（可选）：选取原始未缩放距离更小的那个
                double rawThis = Math.abs(leftSnap - targetLeftPos) +
                        Math.abs(rightSnap - targetRightPos);
                if (bestPair != null) {
                    double rawBest = Math.abs(leftSnap - bestPair.first.position) +
                            Math.abs(rightSnap - bestPair.second.position);
                    if (rawThis < rawBest) {
                        bestPair = candidate;
                    }
                }
            }
        }

        return bestPair;
    }


    public List<List<Integer>> spec2Positions(List<Integer> specs) {
        List<List<Integer>> res = new ArrayList<>();
        int start = mInsets.left;
        int end = mDisplayWidth - mInsets.right;
        int size = end - start;
        for (int spec : specs) {
            List<Integer> pos = new ArrayList<>();
            switch (spec) {
                case SNAP_TO_3_33_33_100:
                    pos.add((int)(start - size * 0.33f));
                    pos.add(start);
                    break;
                case SNAP_TO_3_33_33_66:
                    pos.add(start);
                    pos.add((int)(start + size * 0.33f));
                    break;
                case SNAP_TO_3_33_50_50:
                    pos.add(start);
                    pos.add((int)(start + size * 0.5f));
                    break;
                case SNAP_TO_3_33_66_33:
                    pos.add(start);
                    pos.add((int)(start + size * 0.66f));
                    break;
                case SNAP_TO_3_33_100_33:
                    pos.add(start);
                    pos.add(end);
                    break;
                case SNAP_TO_3_33_66_33_2:
                    pos.add((int)(start + size * 0.33f));
                    pos.add(end);
                    break;
                case SNAP_TO_3_33_33_33:
                    pos.add((int)(start + size * 0.33f));
                    pos.add((int)(start + size * 0.66f));
                    break;
                case SNAP_TO_3_50_50_33:
                    pos.add((int)(start + size * 0.5f));
                    pos.add(end);
                    break;
                case SNAP_TO_3_66_33_33:
                    pos.add((int)(start + size * 0.66f));
                    pos.add(end);
                    break;
                case SNAP_TO_3_100_33_33:
                    pos.add(end);
                    pos.add((int)(end + size * 0.33f));
                    break;
                default:
                    throw new IllegalStateException("UNKnow Snap Targets " + spec);
            }
            res.add(pos);
        }
        return res;
    }

    public class SnapTarget{
        public final int position;
        public final @SplitScreenConstants.SnapPosition int snapPosition;
        public boolean isLeftTarget;
        /**
         * Multiplier used to calculate distance to snap position. The lower this value, the harder
         * it's to snap on this target
         */
        private final float distanceMultiplier;

        public SnapTarget(int position, @SplitScreenConstants.SnapPosition int snapPosition,
                          boolean isLeft) {
            this(position, snapPosition, isLeft, 1f);
        }

        public SnapTarget(int position, @SplitScreenConstants.SnapPosition int snapPosition,
                          boolean isLeft, float distanceMultiplier) {
            this.position = position;
            this.snapPosition = snapPosition;
            this.isLeftTarget = isLeft;
            this.distanceMultiplier = distanceMultiplier;
        }

        public int getPosition() {
            return position;
        }
    }
}
