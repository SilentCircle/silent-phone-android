#ifndef AXOCONTACT_H
#define AXOCONTACT_H

/**
 * @file AxoContact.h
 * @brief Manage data of a partner
 * 
 * The partner's name, an alias name
 * 
 * @ingroup Axolotl++
 * @{
 */
#include <string>
#include <stdint.h>

namespace axolotl {
class AxoContact
{
public:
    AxoContact(const std::string& name, const std::string alias) : name_(name), alias_(alias) {};
    ~AxoContact() {};

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
