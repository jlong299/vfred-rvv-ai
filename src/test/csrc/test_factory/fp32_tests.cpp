#include "../include/test_factory.h"
#include "../include/fp_utils.h"
#include <vector>
#include <cstdio>

void add_fp32_tests(std::vector<TestCase>& tests) {
    // -- FP32 单精度浮点数测试 --
    tests.push_back(TestCase(FADD_Operands_Hex{0xC0A00000, 0xC0E00000}, ErrorType::Precise)); // -5.0f + -7.0f = -12.0f
    tests.push_back(TestCase(FADD_Operands_Hex{0x3F800000, 0x40000000}, ErrorType::Precise)); // 1.0f + 2.0f = 3.0f
    tests.push_back(TestCase(FADD_Operands_Hex{0x40200000, 0x41200000}, ErrorType::Precise)); // 2.5f + 10.0f = 12.5f
    tests.push_back(TestCase(FADD_Operands_Hex{0x00000000, 0x42F6E666}, ErrorType::Precise)); // 0.0f + 123.45f = 123.45f
    tests.push_back(TestCase(FADD_Operands_Hex{0xC2F6E666, 0x00000000}, ErrorType::Precise)); // -123.45f + 0.0f = -123.45f
    tests.push_back(TestCase(FADD_Operands_Hex{0x42F60000, 0xC2860000}, ErrorType::Precise)); // 123.0f + -67.0f = 56.0f
    tests.push_back(TestCase(FADD_Operands_Hex{0x40A00000, 0x42F60000}, ErrorType::Precise)); // 5.0f + 123.0f = 128.0f
    tests.push_back(TestCase(FADD_Operands_Hex{0x40A00000, 0x40E00000}, ErrorType::Precise)); // 5.0f + 7.0f = 12.0f
    tests.push_back(TestCase(FADD_Operands_Hex{0xBF800000, 0x3F800000}, ErrorType::Precise)); // (-1.0f) + 1.0f = 0.0f
    tests.push_back(TestCase(FADD_Operands_Hex{0x3F800000, 0xBF800000}, ErrorType::Precise)); // 1.0f + (-1.0f) = 0.0f
    tests.push_back(TestCase(FADD_Operands_Hex{0xbf7f7861, 0x7bede2c6}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex{0x58800c00, 0x58800400}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex{0x816849E7, 0x00B6D8A2}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex{0x80000000, 0x80000000}, ErrorType::Precise)); // -0.0f + -0.0f = -0.0f

    int num_random_tests_32 = 200;
    ErrorType default_error_type = ErrorType::Precise;
    printf("\n---- Random tests for FP32 ----\n");
    // ---- FP32 任意值随机测试 ----
    for (int i = 0; i < num_random_tests_32; ++i) {
        FADD_Operands_Hex ops = {gen_any_fp32(), gen_any_fp32()};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // ---- 进行不同指数范围的测试 ----
    // 小数范围测试：指数[-50, -10]
    for (int i = 0; i < num_random_tests_32; ++i) {
        FADD_Operands_Hex ops = {gen_random_fp32(-50, -10), gen_random_fp32(-50, -10)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 中等数值范围测试：指数[-10, 10]
    for (int i = 0; i < num_random_tests_32; ++i) {
        FADD_Operands_Hex ops = {gen_random_fp32(-10, 10), gen_random_fp32(-10, 10)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 大数范围测试：指数[10, 50]
    for (int i = 0; i < num_random_tests_32; ++i) {
        FADD_Operands_Hex ops = {gen_random_fp32(10, 50), gen_random_fp32(10, 50)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 更多测试
    for (int i = 0; i < num_random_tests_32; ++i) {
        FADD_Operands_Hex ops = {gen_random_fp32(-126, 20), gen_random_fp32(-126, 20)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    for (int i = 0; i < num_random_tests_32; ++i) {
        FADD_Operands_Hex ops = {gen_random_fp32(-126, 20), gen_random_fp32(-127, -126)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    for (int i = 0; i < num_random_tests_32; ++i) {
        FADD_Operands_Hex ops = {gen_random_fp32(-127, -126), gen_random_fp32(-126, 20)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    for (int i = 0; i < num_random_tests_32; ++i) {
        FADD_Operands_Hex ops = {gen_random_fp32(-127, 10), gen_random_fp32(-127, 10)};
        tests.push_back(TestCase(ops, default_error_type));
    }
} 