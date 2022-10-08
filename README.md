<!--- STARTEXCLUDE --->
## 🔥 Building an E-commerce Website 🔥

[![License Apache2](https://img.shields.io/hexpm/l/plug.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Discord](https://img.shields.io/discord/685554030159593522)](https://discord.com/widget?id=685554030159593522&theme=dark)

<img src="data/img/splash.png?raw=true" align="right" width="400px"/>

## Materials for the Session

It doesn't matter if you join our workshop live or you prefer to do at your own pace, we have you covered. In this repository, you'll find everything you need for this workshop:

- [Slide deck - week 1](./slides_wk1.pdf)
- [Slide deck - week 2](./slides_wk2.pdf)
- [Slide deck - week 3](./slides_wk3.pdf)
- [Slide deck - week 4](./slides_wk4.pdf)
- [Questions and Answers](https://community.datastax.com/)
- [Worskhop code] (https://github.com/datastaxdevs/workshop-ecommerce-app)

If you cannot attend this workshop live, recordings of this workshop and many more is available on [Youtube](https://youtube.com/datastaxdevs).

## 📋 Table of contents

1. [Introduction](#1-introduction)
2. [Create your Database](#2-create-your-cassandra-instance)
3. [Create your schema](#3-create-your-schema)
4. [Populate the dataset](#4-populate-the-data)
5. [Create your Broker](#5-create-your-pulsar-instance)
6. [Setup your application](#6-setup-your-application)
8. [Enable Social Login](#8-enable-social-login)
9. [Start the Application](#9-start-the-application)

## 1. Introduction

Are you building or do you support an e-commerce website?  If so, then this content is for **you**!

Worldwide digital sales in 2020 eclipsed four trillion dollars (USD).  Businesses that want to compete, need a high performing e-commerce website.  Here, we will demonstrate how to build a high performing persistence layer with DataStax **`ASTRA DB`**.

Why does an e-commerce site need to be fast?  Because most consumers will leave a web page or a mobile app if it takes longer than a few seconds to load.  In the content below, we will cover how to build high-performing data models and services, helping you to build a e-commerce site with high throughput and low latency.

## 2. Create Your Cassandra Instance


[🏠 Back to Table of Contents](#-table-of-contents)

## 3. Create your schema

**Introduction**
This section will provide DDL to create three tables inside the "ecommerce" keyspace: category, price, and product.

#### Session 1 - Product data model ####
The `product` table supports all product data queries, and uses `product_id` as a single key.  It has a few columns for specific product data, but any ad-hoc or non-standard properties can be added to the `specifications` map.

The `category` table will support all product navigation service calls.  It is designed to provide recursive, hierarchical navigation without a pre-set limit on the number of levels.  The top-most level only exists as a `parent_id`, and the bottom-most level contains products.

The `price` table was intentionally split-off from product.  There are several reasons for this.  Price data is much more likely to change than pure product data (different read/write patterns).  Also, large enterprises typically have separate teams for product and price, meaning they will usually have different micro-service layers and data stores.

The `featured_product_groups` table was a late-add, to be able to provide some extra "atmosphere" of an e-commerce website.  This way, the UI has a means by which to highlight a few, select products.

#### Session 2 - Shopping Cart data model ####

The `user_carts` table supports cart metadata.  Carts are not expected to be long-lived, so they have a default TTL (time to live) of 60 days (5,184,000 seconds).  Carts also have a `name` as a part of the key, so that the user can have multiple carts (think "wish lists").

The `cart_products` table holds data on the products added to the cart.  The cart uses `product_timestamp` as the first clustering key in descending order; this way products in the cart will be listed with the most-recently-added products at the top.  Like `user_carts`, each entry has a 60 day TTL.

#### Session 3 - User Profile data model ####

The `user` table holds all data on the user, keyed by a single PRIMARY KEY on `user_id`.  It's main features contain TEXT (string) data for common user properties, as well as a collection of `addresses`.  This is because users (especially B-to-B) may have multiple addresses (mail-to, ship-to, bill-to, etc).  The `addresses` collection is built on a special user defined type (UDT) and `FROZEN` to treat the collection as a Binary Large OBject (BLOB) to reduce tombstones (required by CQL).

As mentioned above, the `address` UDT contains properties used for postal contacts.  All properties are of the TEXT datatype.

The `user_by_email` table is intended to be used as a "manual index" on email address. Essentially, it is a lookup table returning the `user_id` associated with an email address.  This is necessary as `user_email` is nigh-unique (in terms of cardinality of values), and thus a CQL secondary index would perform quite poorly.

#### Session 4 - Order Processing System data model ####

The `order_by_id` table holds detail on each order.  It partitions on `order_id` for optimal data distribution, and clusters on `product_name` and `product_id` for sort order.  The columns specific to the order itself (and not a product) are `STATIC` so that they are only stored once (with the partition key).

The `order_by_user` table holds a reference to each order by `user_id`.  The idea, is that this table is queried by `user_id` and the results are shown on an "order history" page for that user.  Then, each order can be clicked-on, revealing the detail contained in the `order_by_id` table.  `order_id` is a TimeUUID (version 1 UUID) type, which is converted into a human-readable timestamp in the service layer.

The `order_status_history` table maintains a history of each status for an order.  It is meant to be used with queries to the `order_by_id` table, so that a user may see the status progression of their order.

### ✅ 3a. Open the CqlConsole on Astra

```sql
use ecommerce;
```

### ✅ 3b. Execute the following CQL script to create the schema

```sql
/* Session 1 - Product data model */
/* category table */
CREATE TABLE IF NOT EXISTS category (
    parent_id UUID,
    category_id UUID,
    name TEXT,
    image TEXT,
    products LIST<TEXT>,
PRIMARY KEY (parent_id,category_id));

/* price table */
CREATE TABLE IF NOT EXISTS price (
    product_id TEXT,
    store_id TEXT,
    value DECIMAL,
PRIMARY KEY(product_id,store_id));

/* product table */
CREATE TABLE IF NOT EXISTS product (
    product_id TEXT,
    product_group TEXT,
    name TEXT,
    brand TEXT,
    model_number TEXT,
    short_desc TEXT,
    long_desc TEXT,
    specifications MAP<TEXT,TEXT>,
    linked_documents MAP<TEXT,TEXT>,
    images SET<TEXT>,
PRIMARY KEY(product_id));

/* featured product groups table */
CREATE TABLE IF NOT EXISTS featured_product_groups (
    feature_id INT,
    category_id UUID,
    name TEXT,
    image TEXT,
    parent_id UUID,
    price DECIMAL,
PRIMARY KEY (feature_id,category_id));

/* Session 2 - Shopping Cart data model */
CREATE TABLE IF NOT EXISTS user_carts (
    user_id uuid,
    cart_name text,
    cart_id uuid,
    cart_is_active boolean,
    user_email text,
    PRIMARY KEY (user_id, cart_name, cart_id)
) WITH default_time_to_live = 5184000;

CREATE TABLE IF NOT EXISTS cart_products (
    cart_id uuid,
    product_timestamp timestamp,
    product_id text,
    product_description text,
    product_name text,
    quantity int,
    PRIMARY KEY (cart_id, product_timestamp, product_id)
) WITH CLUSTERING ORDER BY (product_timestamp DESC, product_id ASC)
  AND default_time_to_live = 5184000;

/* Session 3 - User Profile data model */
CREATE TYPE IF NOT EXISTS address (
  type TEXT,
  mailto_name TEXT,
  street TEXT,
  street2 TEXT,
  city TEXT,
  state_province TEXT,
  postal_code TEXT,
  country TEXT
);

CREATE TABLE IF NOT EXISTS user (
  user_id UUID,
  user_email TEXT,
  picture_url TEXT,
  first_name TEXT,
  last_name TEXT,
  locale TEXT,
  addresses LIST<FROZEN<address>>,
  session_id TEXT,
  password TEXT,
  password_timestamp TIMESTAMP,
  PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS user_by_email (
  user_email TEXT PRIMARY KEY,
  user_id UUID
);

/* Session 4 - Order Processing System data model */
CREATE TABLE IF NOT EXISTS order_by_id (
    order_id timeuuid,
    product_name text,
    product_id text,
    order_shipping_handling decimal static,
    order_status text static,
    order_subtotal decimal static,
    order_tax decimal static,
    order_total decimal static,
    payment_method text static,
    product_price decimal,
    product_qty int,
    shipping_address address static,
    PRIMARY KEY (order_id, product_name, product_id)
) WITH CLUSTERING ORDER BY (product_name ASC, product_id ASC);

CREATE TABLE IF NOT EXISTS order_by_user (
    user_id uuid,
    order_id timeuuid,
    order_status text,
    order_total decimal,
    PRIMARY KEY (user_id, order_id)
) WITH CLUSTERING ORDER BY (order_id DESC);

CREATE TABLE IF NOT EXISTS order_status_history (
    order_id timeuuid,
    status_timestamp timestamp,
    order_status text,
    PRIMARY KEY (order_id, status_timestamp)
) WITH CLUSTERING ORDER BY (status_timestamp DESC);
```

[🏠 Back to Table of Contents](#-table-of-contents)

## 4. Populate the Data

#### ✅ 4a. Execute the following script to populate the tables with the data below

#### Session 1 - Product data ####

```sql
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Clothing',18105592-77aa-4469-8556-833b419dacf4,'ls534.png',ffdac25a-0244-4894-bb31-a0884bc82aa9);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Tech Accessories',5929e846-53e8-473e-8525-80b666c46a83,'',ffdac25a-0244-4894-bb31-a0884bc82aa9);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Cups and Mugs',675cf3a2-2752-4de7-ae2e-849471c29f51,'',ffdac25a-0244-4894-bb31-a0884bc82aa9);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Wall Decor',591bf485-de09-4b46-8fd2-5d9dc7ca101e,'bh001.png',ffdac25a-0244-4894-bb31-a0884bc82aa9);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('T-Shirts',91455473-212e-4c6e-8bec-1da06779ae10,'ls534.png',18105592-77aa-4469-8556-833b419dacf4);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Hoodies',6a4d86aa-ceb5-4c6f-b9b9-80e9a8c58ad1,'',18105592-77aa-4469-8556-833b419dacf4);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Jackets',d887b049-d16c-46e1-8c94-0a1280dedc30,'',18105592-77aa-4469-8556-833b419dacf4);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Mousepads',d04dfb5b-69c6-4e97-b572-e9e390165a84,'',5929e846-53e8-473e-8525-80b666c46a83);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Wrist Rests',aa161129-d456-45ba-b1f0-fac7898b6d06,'',5929e846-53e8-473e-8525-80b666c46a83);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Laptop Covers',1c4b8599-78df-4f93-9c52-578bd959a3a5,'',5929e846-53e8-473e-8525-80b666c46a83);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Cups',7536fdef-fcd9-44a3-9360-0bffd2904408,'',675cf3a2-2752-4de7-ae2e-849471c29f51);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Coffee Mugs',20374300-185c-4ee5-b0bc-77fbdc3a21ed,'',675cf3a2-2752-4de7-ae2e-849471c29f51);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Travel Mugs',0660483e-2fad-447b-b19a-63ab4935e482,'',675cf3a2-2752-4de7-ae2e-849471c29f51);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Posters',fdbe9dcb-6878-4216-a64d-27c094b1b075,'',591bf485-de09-4b46-8fd2-5d9dc7ca101e);
INSERT INTO category (name,category_id,image,parent_id) VALUES ('Wall Art',943482f9-070c-4390-bb30-2107b6fe653a,'bh001.png',591bf485-de09-4b46-8fd2-5d9dc7ca101e);
INSERT INTO category (name,category_id,image,parent_id,products) VALUES ('Men''s "Go Away...Annotation" T-Shirt',99c4d825-d262-4a95-a04e-cc72e7e273c1,'ls534.png',91455473-212e-4c6e-8bec-1da06779ae10,['LS534S','LS534M','LS534L','LS534XL','LS5342XL','LS5343XL']);
INSERT INTO category (name,category_id,image,parent_id,products) VALUES ('Men''s "Your Face...Autowired" T-Shirt',3fa13eee-d057-48d0-b0ae-2d83af9e3e3e,'ln355.png',91455473-212e-4c6e-8bec-1da06779ae10,['LN355S','LN355M','LN355L','LN355XL','LN3552XL','LN3553XL']);
INSERT INTO category (name,category_id,image,parent_id,products) VALUES ('Bigheads',2f25a732-0744-406d-baee-3e8131cbe500,'bh001.png',943482f9-070c-4390-bb30-2107b6fe653a,['bh001','bh002','bh003']);
INSERT INTO category (name,category_id,image,parent_id,products) VALUES ('DataStax Gray Track Jacket',f629e107-b219-4563-a852-6909fd246949,'dss821.jpg',d887b049-d16c-46e1-8c94-0a1280dedc30,['DSS821S','DSS821M','DSS821L','DSS821XL']);
INSERT INTO category (name,category_id,image,parent_id,products) VALUES ('DataStax Vintage 2015 MVP Hoodie',86d234a4-6b97-476c-ada8-efb344d39743,'dsh915.jpg',6a4d86aa-ceb5-4c6f-b9b9-80e9a8c58ad1,['DSH915S','DSH915M','DSH915L','DSH915XL']);
INSERT INTO category (name,category_id,image,parent_id,products) VALUES ('DataStax Black Hoodie',b9bed3c0-0a76-44ea-bce6-f5f21611a3f1,'dsh916.jpg',6a4d86aa-ceb5-4c6f-b9b9-80e9a8c58ad1,['DSH916S','DSH916M','DSH916L','DSH916XL']);
INSERT INTO category (name,category_id,image,parent_id,products) VALUES ('Apache Cassandra 3.0 Contributor T-Shirt',95ae4613-0184-46ee-b4b0-adfe882754a8,'apc30a.jpg',91455473-212e-4c6e-8bec-1da06779ae10,['APC30S','APC30M','APC30L','APC30XL','APC302XL','APC303XL']);
INSERT INTO category (name,category_id,image,parent_id,products) VALUES ('DataStax Astra "One Team" Long Sleeve Tee',775be203-1a84-4822-9645-4da98ca2b2d8,'dsa1121.jpg',91455473-212e-4c6e-8bec-1da06779ae10,['DSA1121S','DSA1121M','DSA1121L','DSA1121XL','DSA11212XL','DSA11213XL']);

INSERT INTO price(product_id,store_id,value) VALUES ('LS534S','web',14.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LS534M','web',14.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LS534L','web',14.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LS534XL','web',14.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LS5342XL','web',16.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LS5343XL','web',16.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LN355S','web',14.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LN355M','web',14.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LN355L','web',14.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LN355XL','web',14.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LN3552XL','web',16.99);
INSERT INTO price(product_id,store_id,value) VALUES ('LN3553XL','web',16.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSA1121S','web',21.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSA1121M','web',21.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSA1121L','web',21.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSA1121XL','web',21.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSA11212XL','web',23.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSA11213XL','web',23.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSS821S','web',44.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSS821M','web',44.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSS821L','web',44.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSS821XL','web',44.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSH915S','web',35.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSH915M','web',35.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSH915L','web',35.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSH915XL','web',35.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSH916S','web',35.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSH916M','web',35.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSH916L','web',35.99);
INSERT INTO price(product_id,store_id,value) VALUES ('DSH916XL','web',35.99);
INSERT INTO price(product_id,store_id,value) VALUES ('APC30S','web',15.99);
INSERT INTO price(product_id,store_id,value) VALUES ('APC30M','web',15.99);
INSERT INTO price(product_id,store_id,value) VALUES ('APC30L','web',15.99);
INSERT INTO price(product_id,store_id,value) VALUES ('APC30XL','web',15.99);
INSERT INTO price(product_id,store_id,value) VALUES ('APC302XL','web',17.99);
INSERT INTO price(product_id,store_id,value) VALUES ('APC303XL','web',17.99);

INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LS534S','LS534','Go Away Annotation T-Shirt','NerdShirts','NS101','Men''s Small "Go Away...Annotation" T-Shirt','Having to answer support questions when you really want to get back to coding?  Wear this to work, and let there be no question as to what you''d rather be doing.',{'size':'Small','material':'cotton, polyester','cut':'men''s','color':'black'},{'ls534.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LS534M','LS534','Go Away Annotation T-Shirt','NerdShirts','NS101','Men''s Medium "Go Away...Annotation" T-Shirt','Having to answer support questions when you really want to get back to coding?  Wear this to work, and let there be no question as to what you''d rather be doing.',{'size':'Medium','material':'cotton, polyester','cut':'men''s','color':'black'},{'ls534.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LS534L','LS534','Go Away Annotation T-Shirt','NerdShirts','NS101','Men''s Large "Go Away...Annotation" T-Shirt','Having to answer support questions when you really want to get back to coding?  Wear this to work, and let there be no question as to what you''d rather be doing.',{'size':'Large','material':'cotton, polyester','cut':'men''s','color':'black'},{'ls534.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LS534XL','LS534','Go Away Annotation T-Shirt','NerdShirts','NS101','Men''s Extra Large "Go Away...Annotation" T-Shirt','Having to answer support questions when you really want to get back to coding?  Wear this to work, and let there be no question as to what you''d rather be doing.',{'size':'Extra Large','material':'cotton, polyester','cut':'men''s','color':'black'},{'ls534.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LS5342XL','LS534','Go Away Annotation T-Shirt','NerdShirts','NS101','Men''s 2x Large "Go Away...Annotation" T-Shirt','Having to answer support questions when you really want to get back to coding?  Wear this to work, and let there be no question as to what you''d rather be doing.',{'size':'2x Large','material':'cotton, polyester','cut':'men''s','color':'black'},{'ls534.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LS5343XL','LS534','Go Away Annotation T-Shirt','NerdShirts','NS101','Men''s 3x Large "Go Away...Annotation" T-Shirt','Having to answer support questions when you really want to get back to coding?  Wear this to work, and let there be no question as to what you''d rather be doing.',{'size':'3x Large','material':'cotton, polyester','cut':'men''s','color':'black'},{'ls534.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LN355S','LN355','Your Face is an @Autowired @Bean T-Shirt','NerdShirts','NS102','Men''s Small "Your Face...Autowired" T-Shirt','Everyone knows that one person who overuses the "your face" jokes.',{'size':'Small','material':'cotton, polyester','cut':'men''s','color':'black'},{'ln355.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LN355M','LN355','Your Face is an @Autowired @Bean T-Shirt','NerdShirts','NS102','Men''s Medium "Your Face...Autowired" T-Shirt','Everyone knows that one person who overuses the "your face" jokes.',{'size':'Medium','material':'cotton, polyester','cut':'men''s','color':'black'},{'ln355.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LN355L','LN355','Your Face is an @Autowired @Bean T-Shirt','NerdShirts','NS102','Men''s Large "Your Face...Autowired" T-Shirt','Everyone knows that one person who overuses the "your face" jokes.',{'size':'Large','material':'cotton, polyester','cut':'men''s','color':'black'},{'ln355.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LN355XL','LN355','Your Face is an @Autowired @Bean T-Shirt','NerdShirts','NS102','Men''s Extra Large "Your Face...Autowired" T-Shirt','Everyone knows that one person who overuses the "your face" jokes.',{'size':'Extra Large','material':'cotton, polyester','cut':'men''s','color':'black'},{'ln355.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LN3552XL','LN355','Your Face is an @Autowired @Bean T-Shirt','NerdShirts','NS102','Men''s 2x Large "Your Face...Autowired" T-Shirt','Everyone knows that one person who overuses the "your face" jokes.',{'size':'2x Large','material':'cotton, polyester','cut':'men''s','color':'black'},{'ln355.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('LN355XL','LN355','Your Face is an @Autowired @Bean T-Shirt','NerdShirts','NS102','Men''s 3x Large "Your Face...Autowired" T-Shirt','Everyone knows that one person who overuses the "your face" jokes.',{'size':'3x Large','material':'cotton, polyester','cut':'men''s','color':'black'},{'ln355.png'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSA1121S','DSA1121','DataStax Astra "One Team" Long Sleeve Tee','DataStax','DSA1121','DataStax Astra "One Team" Long Sleeve Tee - Small','Given out at the internal summit, show how proud you are to talk about the world''s best multi-region, multi-cloud, serverless database!',{'size':'Small','material':'cotton, polyester','color':'black'},{'dsa1121.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSA1121M','DSA1121','DataStax Astra "One Team" Long Sleeve Tee','DataStax','DSA1121','DataStax Astra "One Team" Long Sleeve Tee - Medium','Given out at the internal summit, show how proud you are to talk about the world''s best multi-region, multi-cloud, serverless database!',{'size':'Medium','material':'cotton, polyester','color':'black'},{'dsa1121.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSA1121L','DSA1121','DataStax Astra "One Team" Long Sleeve Tee','DataStax','DSA1121','DataStax Astra "One Team" Long Sleeve Tee - Large','Given out at the internal summit, show how proud you are to talk about the world''s best multi-region, multi-cloud, serverless database!',{'size':'Large','material':'cotton, polyester','color':'black'},{'dsa1121.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSA1121XL','DSA1121','DataStax Astra "One Team" Long Sleeve Tee','DataStax','DSA1121','DataStax Astra "One Team" Long Sleeve Tee - Extra Large','Given out at the internal summit, show how proud you are to talk about the world''s best multi-region, multi-cloud, serverless database!',{'size':'Extra Large','material':'cotton, polyester','color':'black'},{'dsa1121.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSA11212XL','DSA1121','DataStax Astra "One Team" Long Sleeve Tee','DataStax','DSA1121','DataStax Astra "One Team" Long Sleeve Tee - 2X Large','Given out at the internal summit, show how proud you are to talk about the world''s best multi-region, multi-cloud, serverless database!',{'size':'2X Large','material':'cotton, polyester','color':'black'},{'dsa1121.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSA11213XL','DSA1121','DataStax Astra "One Team" Long Sleeve Tee','DataStax','DSA1121','DataStax Astra "One Team" Long Sleeve Tee - 3X Large','Given out at the internal summit, show how proud you are to talk about the world''s best multi-region, multi-cloud, serverless database!',{'size':'3X Large','material':'cotton, polyester','color':'black'},{'dsa1121.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('APC30S','APC30','Apache Cassandra 3.0 Contributor T-Shirt','Apache Foundation','APC30','Apache Cassandra 3.0 Contributor T-Shirt - Small','Own a piece of Cassandra history with this Apache Cassandra 3.0 "Contributor" shirt.  Given out to all of the contributors to the project in 2016, shows the unmistakable Cassandra Eye on the front, with the
engine rebuild" on the back.',{'size':'Small','material':'cotton, polyester','color':'black'},{'apc30.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('APC30M','APC30','Apache Cassandra 3.0 Contributor T-Shirt','Apache Foundation','APC30','Apache Cassandra 3.0 Contributor T-Shirt - Medium','Own a piece of Cassandra history with this Apache Cassandra 3.0 "Contributor" shirt.  Given out to all of the contributors to the project in 2016, shows the unmistakable Cassandra Eye on the front, with the
engine rebuild" on the back.',{'size':'Medium','material':'cotton, polyester','color':'black'},{'apc30.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('APC30L','APC30','Apache Cassandra 3.0 Contributor T-Shirt','Apache Foundation','APC30','Apache Cassandra 3.0 Contributor T-Shirt - Large','Own a piece of Cassandra history with this Apache Cassandra 3.0 "Contributor" shirt.  Given out to all of the contributors to the project in 2016, shows the unmistakable Cassandra Eye on the front, with the
engine rebuild" on the back.',{'size':'Large','material':'cotton, polyester','color':'black'},{'apc30.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('APC30XL','APC30','Apache Cassandra 3.0 Contributor T-Shirt','Apache Foundation','APC30','Apache Cassandra 3.0 Contributor T-Shirt - Extra Large','Own a piece of Cassandra history with this Apache Cassandra 3.0 "Contributor" shirt.  Given out to all of the contributors to the project in 2016, shows the unmistakable Cassandra Eye on the front, with the
engine rebuild" on the back.',{'size':'Extra Large','material':'cotton, polyester','color':'black'},{'apc30.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('APC302XL','APC30','Apache Cassandra 3.0 Contributor T-Shirt','Apache Foundation','APC30','Apache Cassandra 3.0 Contributor T-Shirt - 2X Large','Own a piece of Cassandra history with this Apache Cassandra 3.0 "Contributor" shirt.  Given out to all of the contributors to the project in 2016, shows the unmistakable Cassandra Eye on the front, with the
engine rebuild" on the back.',{'size':'2X Large','material':'cotton, polyester','color':'black'},{'apc30.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('APC303XL','APC30','Apache Cassandra 3.0 Contributor T-Shirt','Apache Foundation','APC30','Apache Cassandra 3.0 Contributor T-Shirt - 3X Large','Own a piece of Cassandra history with this Apache Cassandra 3.0 "Contributor" shirt.  Given out to all of the contributors to the project in 2016, shows the unmistakable Cassandra Eye on the front, with the
engine rebuild" on the back.',{'size':'3X Large','material':'cotton, polyester','color':'black'},{'apc30.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSS821S','DSS821','DataStax Gray Track Jacket','DataStax','DSS821','DataStax Gray Track Jacket - Small','This lightweight polyester jacket will be your favorite while hiking the trails or teeing off.',{'size':'Small','material':'polyester','color':'gray'},{'dss821.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSS821M','DSS821','DataStax Gray Track Jacket','DataStax','DSS821','DataStax Gray Track Jacket - Medium','This lightweight polyester jacket will be your favorite while hiking the trails or teeing off.',{'size':'Medium','material':'polyester','color':'gray'},{'dss821.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSS821L','DSS821','DataStax Gray Track Jacket','DataStax','DSS821','DataStax Gray Track Jacket - Large','This lightweight polyester jacket will be your favorite while hiking the trails or teeing off.',{'size':'Large','material':'polyester','color':'gray'},{'dss821.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSS821XL','DSS821','DataStax Gray Track Jacket','DataStax','DSS821','DataStax Gray Track Jacket - Extra Large','This lightweight polyester jacket will be your favorite while hiking the trails or teeing off.',{'size':'Extra Large','material':'polyester','color':'gray'},{'dss821.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSH915S','DSH915','DataStax Vintage 2015 MVP Hoodie','DataStax','DSS915','DataStax Vintage 2015 MVP Hoodie - Small','Given out to MVPs at the 2015 DataStax Cassandra Summit.  Warm!  You will underestimate how many times you will fall asleep wearing this!',{'size':'Small','color':'black'},{'dsh915.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSH915M','DSH915','DataStax Vintage 2015 MVP Hoodie','DataStax','DSS915','DataStax Vintage 2015 MVP Hoodie - Medium','Given out to MVPs at the 2015 DataStax Cassandra Summit.  Warm!  You will underestimate how many times you will fall asleep wearing this!',{'size':'Medium','color':'black'},{'dsh915.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSH915L','DSH915','DataStax Vintage 2015 MVP Hoodie','DataStax','DSS915','DataStax Vintage 2015 MVP Hoodie - Large','Given out to MVPs at the 2015 DataStax Cassandra Summit.  Warm!  You will underestimate how many times you will fall asleep wearing this!',{'size':'Large','color':'black'},{'dsh915.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSH915XL','DSH915','DataStax Vintage 2015 MVP Hoodie','DataStax','DSS915','DataStax Vintage 2015 MVP Hoodie - Extra Large','Given out to MVPs at the 2015 DataStax Cassandra Summit.  Warm!  You will underestimate how many times you will fall asleep wearing this!',{'size':'Extra Large','color':'black'},{'dsh915.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSH916S','DSH916','DataStax Vintage 2015 MVP Hoodie','DataStax','DSS916','DataStax Black Hoodie - Small','Super warm!  You will underestimate how many times you will fall asleep wearing this!',{'size':'Small','color':'black'},{'dsh916.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSH916M','DSH916','DataStax Vintage 2015 MVP Hoodie','DataStax','DSS916','DataStax Black Hoodie - Medium','Super warm!  You will underestimate how many times you will fall asleep wearing this!',{'size':'Medium','color':'black'},{'dsh916.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSH916L','DSH916','DataStax Vintage 2015 MVP Hoodie','DataStax','DSS916','DataStax Black Hoodie - Large','Super warm!  You will underestimate how many times you will fall asleep wearing this!',{'size':'Large','color':'black'},{'dsh916.jpg'});
INSERT INTO product(product_id,product_group,name,brand,model_number,short_desc,long_desc,specifications,images)
VALUES ('DSH916XL','DSH916','DataStax Black Hoodie','DataStax','DSS916','DataStax Black Hoodie - Extra Large','Super warm!  You will underestimate how many times you will fall asleep wearing this!',{'size':'Extra Large','color':'black'},{'dsh916.jpg'});

INSERT INTO featured_product_groups (feature_id,name,category_id,image,price,parent_id) VALUES (202112,'DataStax Gray Track Jacket',f629e107-b219-4563-a852-6909fd246949,'dss821.jpg',44.99,d887b049-d16c-46e1-8c94-0a1280dedc30);
INSERT INTO featured_product_groups (feature_id,name,category_id,image,price,parent_id) VALUES (202112,'DataStax Black Hoodie',b9bed3c0-0a76-44ea-bce6-f5f21611a3f1,'dsh916.jpg',35.99,6a4d86aa-ceb5-4c6f-b9b9-80e9a8c58ad1);
INSERT INTO featured_product_groups (feature_id,name,category_id,image,price,parent_id) VALUES (202112,'Apache Cassandra 3.0 Contributor T-Shirt',95ae4613-0184-46ee-b4b0-adfe882754a8,'apc30a.jpg',15.99,91455473-212e-4c6e-8bec-1da06779ae10);
INSERT INTO featured_product_groups (feature_id,name,category_id,image,price,parent_id) VALUES (202112,'DataStax Astra "One Team" Long Sleeve Tee',775be203-1a84-4822-9645-4da98ca2b2d8,'dsa1121.jpg',21.99,91455473-212e-4c6e-8bec-1da06779ae10);

```

Although it's not advised to use wildcards as below, you can verify the data has been created with the following command.

```
select * from CATEGORY;
```

**Notes:**

 - The "top" categories of the product hierarchy can be retrieved using a `parent_id` of "ffdac25a-0244-4894-bb31-a0884bc82aa9".
 - Without specifying a `category_id`, all categories for the `parent_id` are returned.
 - When a category from the "bottom" of the hierarchy is queried, a populated `products` ArrayList will be returned.  From there, the returned `product_id`s can be used with the `/product` service.
 - Category navigation is achieved by using the `parent_id` and `category_id` properties returned for each category (to build the "next level" category links).
 - `/category/ffdac25a-0244-4894-bb31-a0884bc82aa9`  =>  Category[Clothing, Cups and Mugs, Tech Accessories, Wall Decor]
 - `/category/ffdac25a-0244-4894-bb31-a0884bc82aa9/18105592-77aa-4469-8556-833b419dacf4`  =>  Category[Clothing]
 - `/category/18105592-77aa-4469-8556-833b419dacf4`  =>  Category[T-Shirts, Hoodies, Jackets]
 - `/category/91455473-212e-4c6e-8bec-1da06779ae10`  =>  Category[Men's "Your Face...Autowired" T-Shirt, Men's "Go Away...Annotation" T-Shirt]
 - The featured products table is a simple way for web marketers to promote small numbers of products, and have them appear in an organized fashion on the main page.  The `feature_id` key is simply an integer, with the default being `202112` (for December, 2021).  You can (of course) use other numeric naming schemes.

[🏠 Back to Table of Contents](#-table-of-contents)

## 5. Create your Pulsar instance

So if you're running on a Mac, you'll likely get a Netty error when trying to resolve DNS.  Need to run it containerized!

```
docker run -it -e PULSAR_PREFIX_webServicePort=8081 -p 6650:6650 -p 8081:8081
    --mount source=pulsardata,target=/pulsar/data --mount source=pulsarconf,target=/pulsar/conf
    apachepulsar/pulsar:2.10.1
    bin/pulsar standalone
```

## 6. Setup your application
Open the `.env` file.

```
CASSANDRA_DB_KEYSPACE=ecommerce
CASSANDRA_DB_ENDPOINTS=127.0.0.1
CASSANDRA_DB_USERNAME=
CASSANDRA_DB_PASSWORD=
CASSANDRA_DB_DC=
PULSAR_STREAM_URL=pulsar://127.0.0.1:6650
PULSAR_STREAM_TENANT=ecommerce-aaron
```

Make sure to instantiate the environment variables by running the following command

```
source .env
```

Verify that the environment variables are properly setup with the following command

```
env | grep "PULSAR\|CASSANDRA"
```

You should see seven environment variables (not shown here).

[🏠 Back to Table of Contents](#-table-of-contents)

## 8. Enable Social Login

Now that we're done with tests, let's `cd` to the top directory.

```
/workspace/workshop-ecommerce-app/
```

On a tab in a browser navigate to [https://console.cloud.google.com/apis/credentials](https://console.cloud.google.com/apis/credentials).

Consent to using APIs and services and you should finally be presented a screen that looks like below and pick values as shown.

![ouath](data/img/Oauthconsent1.png?raw=true)

Pick the appropriate values as shown below and complete the consent.

![ouath](data/img/Oauthconsent2.png?raw=true)

Now click on the `credentials` tab, `+ CREATE CREDENTIALS` tab and finally the `OAuth Client ID` dropdown as shown in the following screen.

![ouath](data/img/Oauthcred0.png?raw=true)

You will be presented with a screen for entering the `Authorized JavaScript Origins` and `Authorized redirect URIs` as shown below.

You'll need the following URIs. Make a note of this. We will use `http` instead of `https` as illustrated below.

For the `Authorized JavaScript Origins` use `http://127.0.0.1/`.

For the `Authorized redirect URIs` use `http://127.0.0.1/login/oauth2/code/google`

Enter the respective values as shown below which enables URI redirection and SSO for the app.

![ouath](data/img/Oauthcred1.png?raw=true)


Make sure you enter the above values correctly as shown and hit `CREATE` on bottom as shown.

![ouath](data/img/Oauthcred2.png?raw=true)

Now you're ready to fetch the credentials  by using the copy 'n paste icons on right as shown below.

![ouath](data/img/Oauthcred3.png?raw=true)

You can copy and paste them in the `application.yml` file as entries for Google SSO authorization as indicated below.

```bash
mv /workspace/workshop-ecommerce-app/backend/src/main/resources/application.yml.sample /workspace/workshop-ecommerce-app/backend/src/main/resources/application.yml
```

and opening and plugging in the values `Your Client ID`
and `Your Client Secret` respectively by using the following command

```
gp open /workspace/workshop-ecommerce-app/backend/src/main/resources/application.yml
```


[🏠 Back to Table of Contents](#-table-of-contents)

## 9. Start the Application

You can install the backend with the credentials using the following command

```
cd /workspace/workshop-ecommerce-app
mvn clean install
```

To start the application, we've provided a very simple convenience script that can be run as below.

```
./start.sh
```

Your e-commerce application should be up and running.

✅ **9d: Check APIs are now available**

Issue the following command in that shell as you did earlier.

```
curl localhost:8080/api/v1/products/product/LS534S
```

You should see some output indicating that the API server is serving our ecommerce APIs.

**👁️ Expected output**

✅ **9f: Get the Open API specification**

In the new shell window open the specification in the preview or browser with the following command

```
gp preview $(gp url 8080)/swagger-ui/index.html
```

The preview window looks like below. **It might help to close all the tabs or open this URL in a browser by clicking on the `open in browser` tab on the top right as shown**.

**👁️ Expected output**

![image](data/img/swagger2.png?raw=true)

Here's how it looks in the browser tab.

![image](data/img/swagger.png?raw=true)

This is the docs for the open APIs that enables the frontend or any other program to obtain the data and manipulate it with REST-based CRUD operations.

The complete app is running in the browser as shown below.

![image](data/img/splash.png?raw=true)

✅ **9g: Use your social login**

Hit login as shown below

![login](data/img/Oauthlogin0.png?raw=true)


You should be presented with the Google SSO Login option. Click on the icon as shown below.

![login](data/img/Oauthlogin1.png?raw=true)

Pick the Google user account and proceed to login as you would with Google.

![login](data/img/Oauthlogin2.png?raw=true)

If all the values are wired properly you should see the following screen with the icon above showing that the authentication worked as below and the `Logout` button now available.

![ouath](data/img/Oauthauthenticated.png?raw=true)

and voila, just like that we are done setting up user profile with Google. We can implement Github and other social logins similarly.

[🏠 Back to Table of Contents](#-table-of-contents)

# Done?

Congratulations: you made to the end of today's workshop. You will notice that the application is still incomplete as we're evolving it. More building to follow!!!

![Badge](data/img/build-an-ecommerce-app.png)

**... and see you at our next workshop!**

> Sincerely yours, The DataStax Developers
