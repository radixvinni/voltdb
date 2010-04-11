/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

/* WARNING: THIS FILE IS AUTO-GENERATED
            DO NOT MODIFY THIS SOURCE
            ALL CHANGES MUST BE MADE IN THE CATALOG GENERATOR */

#include <cassert>
#include "connector.h"
#include "catalog.h"
#include "connectordestinationinfo.h"
#include "connectortableinfo.h"

using namespace catalog;
using namespace std;

Connector::Connector(Catalog *catalog, CatalogType *parent, const string &path, const string &name)
: CatalogType(catalog, parent, path, name),
  m_tableInfo(catalog, this, path + "/" + "tableInfo"), m_destInfo(catalog, this, path + "/" + "destInfo")
{
    CatalogValue value;
    m_fields["loaderclass"] = value;
    m_fields["enabled"] = value;
    m_childCollections["tableInfo"] = &m_tableInfo;
    m_childCollections["destInfo"] = &m_destInfo;
}

void Connector::update() {
    m_loaderclass = m_fields["loaderclass"].strValue.c_str();
    m_enabled = m_fields["enabled"].intValue;
}

CatalogType * Connector::addChild(const std::string &collectionName, const std::string &childName) {
    if (collectionName.compare("tableInfo") == 0) {
        CatalogType *exists = m_tableInfo.get(childName);
        if (exists)
            return NULL;
        return m_tableInfo.add(childName);
    }
    if (collectionName.compare("destInfo") == 0) {
        CatalogType *exists = m_destInfo.get(childName);
        if (exists)
            return NULL;
        return m_destInfo.add(childName);
    }
    return NULL;
}

CatalogType * Connector::getChild(const std::string &collectionName, const std::string &childName) const {
    if (collectionName.compare("tableInfo") == 0)
        return m_tableInfo.get(childName);
    if (collectionName.compare("destInfo") == 0)
        return m_destInfo.get(childName);
    return NULL;
}

void Connector::removeChild(const std::string &collectionName, const std::string &childName) {
    assert (m_childCollections.find(collectionName) != m_childCollections.end());
    if (collectionName.compare("tableInfo") == 0)
        return m_tableInfo.remove(childName);
    if (collectionName.compare("destInfo") == 0)
        return m_destInfo.remove(childName);
}

const string & Connector::loaderclass() const {
    return m_loaderclass;
}

bool Connector::enabled() const {
    return m_enabled;
}

const CatalogMap<ConnectorTableInfo> & Connector::tableInfo() const {
    return m_tableInfo;
}

const CatalogMap<ConnectorDestinationInfo> & Connector::destInfo() const {
    return m_destInfo;
}

