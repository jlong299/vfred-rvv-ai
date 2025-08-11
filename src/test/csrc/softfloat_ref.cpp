#include "include/softfloat_ref.h"
#include "include/fp_utils.h"
#include <cstring> // For memcpy

extern "C" {
#include "softfloat.h"
}

// Helper to convert uint32_t to float32_t
static inline float32_t to_float32_t(uint32_t bits) {
    return (float32_t){ .v = bits };
}

// Helper to convert float32_t to uint32_t
static inline uint32_t from_float32_t(float32_t f) {
    return f.v;
}

// Helper to convert uint16_t to float16_t
static inline float16_t to_float16_t(uint16_t bits) {
    return (float16_t){ .v = bits };
}

// Helper to convert float16_t to uint16_t
static inline uint16_t from_float16_t(float16_t f) {
    return f.v;
}

// ===================================================================
//  SoftFloat based reference functions
// ===================================================================

uint32_t softfloat_add_fp32(uint32_t a, uint32_t b) {
    // Set rounding mode to Round-to-Nearest-Even (default)
    softfloat_roundingMode = softfloat_round_near_even;
    
    float32_t f32_a = to_float32_t(a);
    float32_t f32_b = to_float32_t(b);
    
    float32_t result = f32_add(f32_a, f32_b);
    
    return from_float32_t(result);
}

uint16_t softfloat_add_fp16(uint16_t a, uint16_t b) {
    // Set rounding mode
    softfloat_roundingMode = softfloat_round_near_even;
    
    float16_t f16_a = to_float16_t(a);
    float16_t f16_b = to_float16_t(b);
    
    float16_t result = f16_add(f16_a, f16_b);
    
    return from_float16_t(result);
}

// For BFloat16, we leverage the existing conversion functions in `fp_utils.h`
// to bridge between standard types and SoftFloat types.
uint16_t softfloat_add_bf16(uint16_t a, uint16_t b) {
    // Set rounding mode
    softfloat_roundingMode = softfloat_round_near_even;

    // 1. Convert BF16 inputs (uint16_t) to standard C++ float using fp_utils
    float float_a = bf16_to_fp32(a);
    float float_b = bf16_to_fp32(b);

    // 2. Get the bit patterns of the floats
    uint32_t bits_a, bits_b;
    memcpy(&bits_a, &float_a, sizeof(uint32_t));
    memcpy(&bits_b, &float_b, sizeof(uint32_t));

    // 3. Wrap bit patterns into SoftFloat types
    float32_t f32_a = to_float32_t(bits_a);
    float32_t f32_b = to_float32_t(bits_b);
    
    // 4. Perform addition in FP32 using SoftFloat
    float32_t f32_result = f32_add(f32_a, f32_b);
    
    // 5. Unwrap the result bits
    uint32_t result_bits = from_float32_t(f32_result);

    // 6. Convert result bits back to a standard C++ float
    float float_result;
    memcpy(&float_result, &result_bits, sizeof(uint32_t));

    // 7. Convert the float result back to BF16 (uint16_t) using fp_utils
    return fp32_to_bf16(float_result);
} 