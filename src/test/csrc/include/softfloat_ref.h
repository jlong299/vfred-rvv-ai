#ifndef __SOFTFLOAT_REF_H__
#define __SOFTFLOAT_REF_H__

#include <cstdint>

// FP32 a + b, inputs and output are in uint32_t bit format
uint32_t softfloat_add_fp32(uint32_t a, uint32_t b);

// FP16 a + b, inputs and output are in uint16_t bit format
uint16_t softfloat_add_fp16(uint16_t a, uint16_t b);

// BF16 a + b, inputs and output are in uint16_t bit format
uint16_t softfloat_add_bf16(uint16_t a, uint16_t b);

#endif // __SOFTFLOAT_REF_H__ 