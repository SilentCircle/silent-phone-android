/*
Copyright 2016-2017 Silent Circle, LLC

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
#ifndef AXOCONTACT_H
#define AXOCONTACT_H

/**
 * @file ZinaContact.h
 * @brief Manage data of a partner
 * 
 * @ingroup Zina
 * @{
 */
#include <string>
#include <stdint.h>

namespace zina {

/**
 * The partner's name, an alias name
 *
 */
class ZinaContact
{
public:
    ZinaContact(const std::string& name, const std::string& alias) : name_(name), alias_(alias) {};
    ~ZinaContact() {};

    const std::string& getName() const { return name_; }
    const std::string& getAlias() const { return alias_; }
    
    void setAlias(const std::string& alias) { alias_ = alias; }

private:
    std::string name_;
    std::string alias_;
};
}
/**
 * @}
 */

#endif // AXOCONTACT_H
