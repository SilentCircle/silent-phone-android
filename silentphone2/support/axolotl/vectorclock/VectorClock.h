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
// Created by werner on 26.01.17.
//

#ifndef LIBZINA_VECTORCLOCK_H
#define LIBZINA_VECTORCLOCK_H

/**
 * @file
 * @brief Functions to create, manage and compare Vector Clocks
 * @ingroup Zina
 * @{
 */

#include <map>
#include <memory>

/**
 * @brief Groups the vector clock implementation
 */
namespace vectorclock {

    /**
     * @brief Result codes when comparing two vector clocks
     *
     */
    enum Ordering {
        None = 0,               //!< Illegal vector clock, cannot compare
        Equal,                  //!< Both vector clocks are equal
        Before,                 //!< The compared vector clock (this) is **Before** the comparing vector clock (is smaller)
        After,                  //!< The compared vector clock (this) is **After** the comparing vector clock (is bigger)
        Concurrent              //!< Concurrent events happened, neither vector clock 'descends' from the other
    };

    /**
     * @brief Template class to create, manage, and compare Vector Clocks
     *
     * The template supports different types of the node identifier, the clock value
     * is a unsigned 64-bit integer, thus plenty of versions available.
     *
     * @tparam nodeIdType data type of the node identifier
     * @tparam Compare compare function for the node identifier data type. For standard types such as
     *                 integers, strings etc the implementation uses the standard less function by
     *                 default.
     */
    template<typename nodeIdType, class Compare=std::less<nodeIdType> >
    class VectorClock {

    public:
        typedef std::pair<const nodeIdType, int64_t> nodeClockType;

        /**
         * @brief Create an empty vector clock map, set update timestamp to current system time.
         */
        explicit VectorClock() {}

        ~VectorClock() {};

        /**
         * @brief Return the size (number) of the vector clocks
         * @return the number of vector clocks in this vector
         */
        size_t size() const           { return nodeClocks.size(); }

        /**
         * @brief Get the node's clock value.
         * @param node node identifier.
         * @return the node's clock value or @c 0 if the node does not exist in the map.
         */
        int64_t getNodeClock(const nodeIdType &node) const;

        /**
         * @brief Compute an return the sum of clock values.
         *
         * @return Sum of values
         */
        int64_t sumOfValues() const;

        /**
         * @brief Increment a node's clock value.
         *
         * If the node is not set in the vector clock then add it and set its value to 1.
         *
         * @param node node identifier
         * @return @c true if clock was incremented, @c false in case of error
         */
        bool incrementNodeClock(const nodeIdType &node);

        /**
         * @brief Insert node with a clock value.
         *
         * Try to insert a node with a give clock value into the clock vector.
         *
         * @param node node identifier
         * @param value the clock value
         * @return @c true if node was inserted, @c false in case of error, usually if the node already
         *            exists in the map
         */
        bool insertNodeWithValue(const nodeIdType &node, int64_t value) {
            return nodeClocks.insert(nodeClockType(node, value)).second;
        }

        /**
         * @brief Compare this vector clock (compared) with another (comparing) vector clock
         *
         * @param other The comparing vector clock
         * @return @c Ordering of the clocks
         */
        Ordering compare(const VectorClock<nodeIdType, Compare> &other) const;

        /**
         * @brief Merge two Vector Clocks
         *
         * Merge this vector clock with the other vector clock and return a merged vector clock.
         * The merged vector clock contains the nodes of both vector clocks and the clock value
         * of each node is the maximum of matching nodes (nodes with equal node identifier).
         *
         * @param other The other vector clock to merge
         * @return The merged vector clock
         */
        std::shared_ptr<VectorClock<nodeIdType, Compare> > merge(const VectorClock &other) const;

        typename std::map<const nodeIdType, int64_t, Compare>::const_iterator cbegin() const { return nodeClocks.cbegin(); }
        typename std::map<const nodeIdType, int64_t, Compare>::const_iterator cend() const   { return nodeClocks.cend(); }


    private:
        VectorClock(const VectorClock &other) = delete;

        VectorClock &operator=(const VectorClock &other) = delete;

        bool operator==(const VectorClock &other) const = delete;

        const std::map<const nodeIdType, int64_t, Compare>& getNodeClocks() const { return nodeClocks; }

        std::map<const nodeIdType, int64_t, Compare> nodeClocks; //!< Stores the logical clock for each known node
    };

    /* *****************************************************************************************
     * Template implementations of functions which require more than one or two lines
     * to enhance readability of the class declaration.
     ***************************************************************************************** */

    template<typename nodeIdType, class Compare>
    int64_t VectorClock<nodeIdType, Compare>::getNodeClock(const nodeIdType &node) const {
        auto it = nodeClocks.find(node);

        // If no node then return 0. Node map is treated like a sparse array returning 0 on onn-existing index
        if (it == nodeClocks.end()) {
            return 0;
        }
        return it->second;
    }

    template<typename nodeIdType, class Compare>
    bool VectorClock<nodeIdType, Compare>::incrementNodeClock(const nodeIdType &node) {
        auto it = nodeClocks.find(node);

        // If no node then add with value 1
        if (it == nodeClocks.end()) {
            return insertNodeWithValue(node, 1);
        }
        it->second++;
        return true;
    }

    template<typename nodeIdType, class Compare>
    int64_t VectorClock<nodeIdType, Compare>::sumOfValues() const
    {
        const auto end = nodeClocks.cend();

        int64_t sum = 0;
        for (auto it = nodeClocks.cbegin(); it != end; ++it) {
            sum += it->second;
        }
        return sum;
    }

    template<typename nodeIdType, class Compare>
    Ordering VectorClock<nodeIdType, Compare>::compare(const VectorClock<nodeIdType, Compare> &other) const {
        bool thisBigger = false;
        bool otherBigger = false;

        // Merge this and the other vector clock to get a combined set. The loop below uses the
        // node identifiers only to get the clock values of the two vector clocks (this and other)
        auto allNodes = merge(other);
        const size_t allSize = allNodes->size();

        // if 'this' has more nodes than allNodes, then 'this' has clocks that 'other' does not have
        if (size() > allSize) {
            thisBigger = true;
        }
        // if 'other' has more nodes than allNodes, then 'other' has clocks that 'this' does not have
        if (other.size() > allSize) {
            otherBigger = true;
        }
        const auto &allClocks = allNodes->getNodeClocks();
        const auto end = allClocks.cend();

        for (auto it = allClocks.cbegin(); it != end; ++it) {
            if (thisBigger && otherBigger) {    // if both are 'bigger' then it was a concurrent update, no need to check more
                break;
            }
            uint64_t thisClock = getNodeClock(it->first);
            uint64_t otherClock = other.getNodeClock(it->first);

            if (thisClock > otherClock) {
                thisBigger = true;
            }
            else if (otherClock > thisClock) {
                otherBigger = true;
            }
        }

        if (!thisBigger && !otherBigger) {  // if none is 'bigger' then both are equal
            return Equal;
        }
        if (thisBigger && !otherBigger) {   // 'this' is 'bigger', thus updated _after_ other
            return After;
        }
        if (!thisBigger) {                  // 'this' is 'smaller', thus updated _before_ other
            return Before;
        }
        return Concurrent;     // Both are 'bigger', thus concurrent updates
    }

    template<typename nodeIdType, class Compare>
    std::shared_ptr<VectorClock<nodeIdType, Compare> >
    VectorClock<nodeIdType, Compare>::merge(const VectorClock<nodeIdType, Compare> &other) const {

        auto mergedClock = std::make_shared<VectorClock<nodeIdType, Compare> >();
        const auto end = nodeClocks.cend();

        for(auto it = nodeClocks.cbegin(); it != end; ++it) {
            mergedClock->insertNodeWithValue(it->first, it->second);
        }

        // 'newClock' is a copy of the current clocks in 'this' vector clock, get its clocks
        // and merge in the other clocks
        auto &mergedClocks = mergedClock->nodeClocks;
        const auto mergedEnd = mergedClocks.end();

        const auto &otherClocks = other.getNodeClocks();
        const auto otherEnd = otherClocks.cend();

        for (auto it = otherClocks.cbegin(); it != otherEnd; ++it) {
            auto newIt = mergedClocks.find(it->first);

            // Don't know this node yet, insert it with its clock value, otherwise set the maximum
            // of known value and other value
            if (newIt == mergedEnd) {
                mergedClock->insertNodeWithValue(it->first, it->second);
            }
            else {
                newIt->second = std::max(newIt->second, it->second);
            }
        }
        return mergedClock;
    }
}

/**
 * @}
 */
#endif //LIBZINA_VECTORCLOCK_H
