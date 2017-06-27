/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#if 0
#include "condition_variable_posix.h"

#include <errno.h>
#if defined(WEBRTC_LINUX)
#include <ctime>
#else
#include <sys/time.h>
#endif

//#include "critical_section_posix.h"

namespace webrtc {

ConditionVariableWrapper* ConditionVariablePosix::Create() {
  ConditionVariablePosix* ptr = new ConditionVariablePosix;
  if (!ptr) {
    return NULL;
  }

  const int error = ptr->Construct();
  if (error) {
    delete ptr;
    return NULL;
  }

  return ptr;
}

ConditionVariablePosix::ConditionVariablePosix() {
}

int ConditionVariablePosix::Construct() {

  return 0;
}

ConditionVariablePosix::~ConditionVariablePosix() {
  pthread_cond_destroy(&cond_);
}

void ConditionVariablePosix::SleepCS(CriticalSectionWrapper& crit_sect) {
 
}

bool ConditionVariablePosix::SleepCS(CriticalSectionWrapper& crit_sect,
                                     unsigned long max_time_inMS) {

    return true;
}

void ConditionVariablePosix::Wake() {

}

void ConditionVariablePosix::WakeAll() {
}

} // namespace webrtc
#endif
