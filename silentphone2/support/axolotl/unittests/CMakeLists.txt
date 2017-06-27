# Copyright 2016 Silent Circle, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.



cmake_minimum_required(VERSION 3.0)


include_directories(${gtest_SOURCE_DIR}/include)

add_executable(hkdf_test HKDF_test.cpp)
target_link_libraries(hkdf_test gtest_main ${zinaLibName})


add_executable(conv_test conversation.cpp)
target_link_libraries(conv_test gtest_main ${zinaLibName})

add_executable(staged_test stagedKeyStore.cpp)
target_link_libraries(staged_test gtest_main ${zinaLibName})

add_executable(zrtp_ratchet zrtpRatchet.cpp)
target_link_libraries(zrtp_ratchet gtest_main ${zinaLibName})

add_executable(crypto_test cryptoTests.cpp)
target_link_libraries(crypto_test gtest_main ${zinaLibName})
# 
add_executable(store_test storeTests.cpp)
target_link_libraries(store_test gtest_main ${zinaLibName})

# add_executable(keymngmnt_test keymngmt.cpp)
# target_link_libraries(keymngmnt_test gtest_main ${zinaLibName})
# 
add_executable(interface_test interface.cpp)
target_link_libraries(interface_test gtest_main ${zinaLibName})

add_executable(apprepo_test appRepo.cpp)
target_link_libraries(apprepo_test gtest_main ${zinaLibName})

add_executable(scloud_test scloudTest.cpp)
target_link_libraries(scloud_test gtest_main ${zinaLibName})

add_executable(versioning_test VersioningTests.cpp)
target_link_libraries(versioning_test gtest_main ${zinaLibName})

add_executable(changesetsend_test ChangesetSendTests.cpp)
target_link_libraries(changesetsend_test gtest_main ${zinaLibName})

add_executable(changesetrecv_test ChangesetRecvTests.cpp)
target_link_libraries(changesetrecv_test gtest_main ${zinaLibName})

# 
# ############## Java testing #####################
# 
# find_package(Java REQUIRED)
# include(UseJava)

# enable_testing()

# set(CMAKE_JAVA_COMPILE_FLAGS "-source" "1.6" "-target" "1.6")
# set(CMAKE_JAVA_INCLUDE_PATH ${CMAKE_SOURCE_DIR}/interfaceApp/java)

# add_jar(JavaTester JavaTester.java CloudTester.java ${CMAKE_SOURCE_DIR}/interfaceApp/java/ZinaNative.java)

# get_target_property(_jarFile JavaTester JAR_FILE)
# get_target_property(_classDir JavaTester CLASSDIR)

# message(STATUS "Jar file ${_jarFile}")
# message(STATUS "Class compiled to ${_classDir}")

add_test(NAME TestJava COMMAND ${Java_JAVA_EXECUTABLE} -cp ${_jarFile} JavaTester)
