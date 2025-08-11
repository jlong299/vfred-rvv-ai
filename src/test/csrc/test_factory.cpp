#include "include/test_factory.h"
#include "include/fp_utils.h"

#include <vector>
#include <cstdio>

std::vector<TestCase> create_all_tests() {
    std::vector<TestCase> tests;
  
    bool test_fp32 = true;
    bool test_fp16 = true;
    bool test_bf16 = true;
    bool test_fp16_widen = true;
    bool test_bf16_widen = true;
  
    if (test_fp32) {
        add_fp32_tests(tests);
    }

    if (test_fp16) {
        add_fp16_tests(tests);
    }

    if (test_bf16) {
        add_bf16_tests(tests);
    }

    if (test_fp16_widen) {
        add_fp16_widen_tests(tests);
    }

    if (test_bf16_widen) {
        add_bf16_widen_tests(tests);
    }

    return tests;
} 