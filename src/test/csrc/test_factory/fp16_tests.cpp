#include "../include/test_factory.h"
#include "../include/fp_utils.h"
#include <vector>
#include <cstdio>

void add_fp16_tests(std::vector<TestCase>& tests) {
    // -- FP16 并行双路半精度浮点数测试 --
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x3c00, 0x4000}, FADD_Operands_Hex_16{0x4200, 0x3c00}, ErrorType::Precise)); // 1.0 + 2.0 = 3.0 | 3.0 + 1.0 = 4.0
    tests.push_back(TestCase(FADD_Operands_Hex_16{0xbc00, 0x4000}, FADD_Operands_Hex_16{0x3c00, 0xc000}, ErrorType::Precise)); // -1.0 + 2.0 = 1.0 | 1.0 + -2.0 = -1.0
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x3c00, 0xbc00}, FADD_Operands_Hex_16{0x4400, 0x3800}, ErrorType::Precise)); // 1.0 + -1.0 = 0.0 | 4.0 + 0.5 = 4.5
    // 零值测试
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x0000, 0x4000}, FADD_Operands_Hex_16{0x4000, 0x0000}, ErrorType::Precise)); // 0.0 + 2.0 = 2.0 | 2.0 + 0.0 = 2.0
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x3c00, 0x0000}, FADD_Operands_Hex_16{0x0000, 0x4200}, ErrorType::Precise)); // 1.0 + 0.0 = 1.0 | 0.0 + 3.0 = 3.0
    // 无穷大测试
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x7c00, 0x4000}, FADD_Operands_Hex_16{0x3c00, 0xfc00}, ErrorType::Precise)); // +inf + 2.0 = +inf | 1.0 + -inf = -inf
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x3c00, 0x7c00}, FADD_Operands_Hex_16{0x7c00, 0x7c00}, ErrorType::Precise)); // 1.0 + +inf = +inf | +inf + inf = +inf
    // 非规格化数测试
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x0001, 0x4000}, FADD_Operands_Hex_16{0x03ff, 0x3c00}, ErrorType::Precise)); // 最小非规格化数 + 2.0 | 最大非规格化数 + 1.0
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x3c00, 0x0001}, FADD_Operands_Hex_16{0x4000, 0x03ff}, ErrorType::Precise)); // 1.0 + 最小非规格化数 | 2.0 + 最大非规格化数
    // 1.0 + -1.0 = 0.0 | -1.0 + 1.0 = 0.0
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x3c00, 0xbc00}, FADD_Operands_Hex_16{0xbc00, 0x3c00}, ErrorType::Precise));
    // 其他
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x4d6f, 0x1ea8}, FADD_Operands_Hex_16{0x5455, 0xe39c}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x668, 0x5b00}, FADD_Operands_Hex_16{0x8f63, 0x575}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_16{0xdcd9, 0x1054}, FADD_Operands_Hex_16{0xf800, 0x251b}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_16{0x1f00, 0x4163}, FADD_Operands_Hex_16{0x7445, 0x5adb}, ErrorType::Precise));

    printf("\n---- Random tests for FP16 ----\n");
    int num_random_tests_16 = 200;
    ErrorType default_error_type = ErrorType::Precise;
    // ---- FP16 任意值随机测试 ----
    for (int i = 0; i < num_random_tests_16; ++i) {
        FADD_Operands_Hex_16 ops1 = {gen_any_fp16(), gen_any_fp16()};
        FADD_Operands_Hex_16 ops2 = {gen_any_fp16(), gen_any_fp16()};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // ---- 进行不同指数范围的FP16随机测试 ----
    // 小数范围测试：指数[-15, -5]
    for (int i = 0; i < num_random_tests_16; ++i) {
        FADD_Operands_Hex_16 ops1 = {gen_random_fp16(-15, -5), gen_random_fp16(-15, -5)};
        FADD_Operands_Hex_16 ops2 = {gen_random_fp16(-15, -5), gen_random_fp16(-15, -5)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 中等数值范围测试：指数[-5, 5]
    for (int i = 0; i < num_random_tests_16; ++i) {
        FADD_Operands_Hex_16 ops1 = {gen_random_fp16(-5, 5), gen_random_fp16(-5, 5)};
        FADD_Operands_Hex_16 ops2 = {gen_random_fp16(-5, 5), gen_random_fp16(-5, 5)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 大数范围测试：指数[5, 15]
    for (int i = 0; i < num_random_tests_16; ++i) {
        FADD_Operands_Hex_16 ops1 = {gen_random_fp16(5, 15), gen_random_fp16(5, 15)};
        FADD_Operands_Hex_16 ops2 = {gen_random_fp16(5, 15), gen_random_fp16(5, 15)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 更多测试
    for (int i = 0; i < num_random_tests_16; ++i) {
        FADD_Operands_Hex_16 ops1 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, 15)};
        FADD_Operands_Hex_16 ops2 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, 15)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    for (int i = 0; i < num_random_tests_16; ++i) {
        FADD_Operands_Hex_16 ops1 = {gen_random_fp16(-15, -14), gen_random_fp16(-15, 15)};
        FADD_Operands_Hex_16 ops2 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, -14)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    for (int i = 0; i < num_random_tests_16; ++i) {
        FADD_Operands_Hex_16 ops1 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, -14)};
        FADD_Operands_Hex_16 ops2 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, -14)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    for (int i = 0; i < num_random_tests_16; ++i) {
        FADD_Operands_Hex_16 ops1 = {gen_random_fp16(-15, -14), gen_random_fp16(-15, -14)};
        FADD_Operands_Hex_16 ops2 = {gen_random_fp16(-15, -14), gen_random_fp16(-15, -14)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
} 