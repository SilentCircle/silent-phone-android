/*
Copyright 2017 Silent Circle, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

//
// Created by werner on 29.01.17.
//

#include "VectorHelper.h"

using namespace zina;
using namespace std;

int32_t zina::readLocalVectorClock(SQLiteStoreConv &store, const string& groupId, GroupUpdateType type, LocalVClock *vectorClock)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (type == TYPE_NONE || !GroupUpdateType_IsValid(type)) {
        return WRONG_UPDATE_TYPE;
    }

    string serializedData;
    int32_t result = store.loadVectorClock(groupId, type, &serializedData);
    if (SQL_FAIL(result)) {
        return GROUP_ERROR_BASE + result;   // Error return is -400 + sql code
    }
    if (serializedData.empty()) {
        return NO_VECTOR_CLOCK;
    }
    if (!vectorClock->ParseFromArray(serializedData.data(), static_cast<int32_t>(serializedData.size()))) {
        return NO_VECTOR_CLOCK;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

void zina::deserializeVectorClock(const google::protobuf::RepeatedPtrField<VClock> &protoVc, vectorclock::VectorClock<string> *vc)
{
    int32_t numClocks = protoVc.size();

    for (int i = 0; i < numClocks; i++) {
        const VClock &vcData = protoVc.Get(i);
        vc->insertNodeWithValue(vcData.device_id(), vcData.value());
    }
}

int32_t zina::storeLocalVectorClock(SQLiteStoreConv &store, const string& groupId, GroupUpdateType type, const LocalVClock &vectorClock)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (type == TYPE_NONE || !GroupUpdateType_IsValid(type)) {
        return WRONG_UPDATE_TYPE;
    }

    string serializedData;
    if (!vectorClock.SerializeToString(&serializedData)) {
        return GENERIC_ERROR;
    }

    int32_t result = store.insertReplaceVectorClock(groupId, type, serializedData);
    if (SQL_FAIL(result)) {
        return GROUP_ERROR_BASE + result;   // Error return is -400 + sql code
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

void zina::serializeVectorClock(const vectorclock::VectorClock<string> &vc, google::protobuf::RepeatedPtrField<VClock> *changeVc) {
    const auto end = vc.cend();

    changeVc->Clear();
    for (auto it = vc.cbegin(); it != end; ++it) {
        VClock *vectorClock = changeVc->Add();
        vectorClock->set_device_id(it->first);
        vectorClock->set_value(it->second);
    }
}
