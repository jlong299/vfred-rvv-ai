#ifndef __FP_UTILS_H__
#define __FP_UTILS_H__

#include <cstdint>

// FP16 (half-precision) format: 1 sign, 5 exponent, 10 mantissa
typedef uint16_t fp16_t;
// BF16 (bfloat16) format: 1 sign, 8 exponent, 7 mantissa
typedef uint16_t bf16_t;

// --- Floating-point conversion functions ---
float fp16_to_fp32(fp16_t h);
uint16_t fp32_to_fp16(float fp32);
float bf16_to_fp32(bf16_t h);
uint16_t fp32_to_bf16(float fp32);

// --- Random floating-point generation functions ---

// Generates a random FP32 number within a specified exponent range
uint32_t gen_random_fp32(int exp_min, int exp_max);

// Generates a random FP16 number within a specified exponent range
uint16_t gen_random_fp16(int exp_min, int exp_max);

// Generates a random BF16 number within a specified exponent range
uint16_t gen_random_bf16(int exp_min, int exp_max);

// Generates any random FP32 number (excluding NaN)
uint32_t gen_any_fp32();

// Generates any random FP16 number (excluding NaN)
uint16_t gen_any_fp16();

// Generates any random BF16 number (excluding NaN)
uint16_t gen_any_bf16();

#endif // __FP_UTILS_H__ 