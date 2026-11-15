//! Batched chapter statistics for `KomiKita`. The algorithm uses primitive contiguous storage.
use jni::{
    JNIEnv,
    objects::{JClass, JDoubleArray},
    sys::jint,
};
use std::collections::TryReserveError;
use std::panic::{AssertUnwindSafe, catch_unwind};

const MAX_NATIVE_CHAPTERS: usize = 1_000_000;

/// Counts gaps between non-negative chapter numbers, starting at chapter one.
/// Fractions share their base chapter. Invalid numbers are ignored and counts saturate.
///
/// # Panics
///
/// Panics if the chapter buffer cannot be allocated.
#[must_use]
pub fn missing_chapters_count(numbers: &[f64]) -> i32 {
    try_missing_chapters_count(numbers).expect("could not allocate the chapter buffer")
}

#[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
fn try_missing_chapters_count(numbers: &[f64]) -> Result<i32, TryReserveError> {
    let mut chapters = Vec::new();
    chapters.try_reserve_exact(numbers.len())?;
    chapters.extend(
        numbers
            .iter()
            .copied()
            .filter(|n| n.is_finite() && *n >= 0.0 && *n <= f64::from(i32::MAX))
            .map(|n| n as i32),
    );
    chapters.sort_unstable();
    let mut previous = 0_i64;
    let mut missing = 0_i64;
    for chapter in chapters {
        let current = i64::from(chapter);
        if current > previous + 1 {
            missing += current - previous - 1;
        }
        previous = current;
    }
    Ok(i32::try_from(missing).unwrap_or(i32::MAX))
}

// SAFETY: This unique symbol is called only by the matching JVM static native method.
// JNI wrappers retain JVM lifetimes; no Java references or buffers escape this call.
#[allow(unsafe_code, clippy::needless_pass_by_value)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_app_komikita_core_nativecore_NativeChapterEngine_nativeMissingChaptersCount(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    numbers: JDoubleArray<'_>,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        if numbers.is_null() {
            return -1;
        }
        let Ok(length) = env.get_array_length(&numbers) else {
            return -1;
        };
        let Ok(length) = usize::try_from(length) else {
            return -1;
        };
        // Keep native temporary storage bounded; Kotlin handles larger inputs.
        if length > MAX_NATIVE_CHAPTERS {
            return -1;
        }
        let mut values = Vec::new();
        if values.try_reserve_exact(length).is_err() {
            return -1;
        }
        values.resize(length, 0.0);
        if env
            .get_double_array_region(&numbers, 0, &mut values)
            .is_err()
        {
            return -1;
        }
        try_missing_chapters_count(&values).unwrap_or(-1)
    }))
    .unwrap_or(-1)
}

#[cfg(test)]
mod tests {
    use super::missing_chapters_count;
    #[test]
    fn empty_and_unknown() {
        assert_eq!(missing_chapters_count(&[]), 0);
        assert_eq!(
            missing_chapters_count(&[-1.0, -2.0, f64::NAN, f64::INFINITY]),
            0
        );
    }
    #[test]
    fn fractions_and_duplicates_share_a_chapter() {
        assert_eq!(missing_chapters_count(&[1.0, 1.1, 1.5, 1.99, 1.0]), 0);
    }
    #[test]
    fn unsorted_gaps() {
        assert_eq!(
            missing_chapters_count(&[11.0, 1.0, -1.0, 4.0, 2.2, 6.0, 10.0, 2.0]),
            5
        );
    }
    #[test]
    fn chapter_zero_and_large_gaps() {
        assert_eq!(missing_chapters_count(&[0.0, 1.0, 3.0]), 1);
        assert_eq!(missing_chapters_count(&[f64::from(i32::MAX)]), i32::MAX - 1);
    }
    #[test]
    fn ignores_values_outside_chapter_range() {
        assert_eq!(
            missing_chapters_count(&[-0.5, 1e30, f64::NEG_INFINITY, 2.0]),
            1
        );
    }
    #[test]
    #[allow(clippy::cast_possible_truncation)]
    fn matches_reference_over_varied_lists() {
        let mut seed = 42_u64;
        for length in 0..300 {
            let numbers: Vec<f64> = (0..length)
                .map(|_| {
                    seed = seed.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1);
                    f64::from(u32::try_from(seed >> 32).unwrap() % 600) / 2.0
                })
                .collect();
            let mut unique: Vec<i32> = numbers.iter().map(|n| *n as i32).collect();
            unique.sort_unstable();
            unique.dedup();
            let expected = unique.last().copied().unwrap_or(0)
                - i32::try_from(unique.iter().filter(|n| **n > 0).count()).unwrap();
            assert_eq!(missing_chapters_count(&numbers), expected);
        }
    }
}
