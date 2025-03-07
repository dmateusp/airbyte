# Exchange Rates API

## Overview

The exchange rates integration is a toy integration to demonstrate how Airbyte works with a very simple source.

It pulls all its data from [https://exchangeratesapi.io](https://exchangeratesapi.io)

#### Output schema

It contains one stream: `exchange_rates`

Each record in the stream contains many fields:

* The date of the record
* One field for every supported [currency](https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html) which contain the value of that currency on that date.

#### Data type mapping

Currencies are `number` and the date is a `string`.

#### Features

| Feature | Supported? |
| :--- | :--- |
| Full Refresh Sync | Yes |
| Incremental - Append Sync | Yes |
| Namespaces | No |

### Getting started

### Requirements

* API Access Key

### Setup guide

In order to get an `API Access Key` please go to [this](https://manage.exchangeratesapi.io/signup/free) page and enter needed info. After registration 
and login you will see your `API Access Key`, also you may find it [here](https://manage.exchangeratesapi.io/dashboard).

If you have `free` subscription plan (you may check it [here](https://manage.exchangeratesapi.io/plan)) this means that you will have 2 limitations:
1. 1k api calls per month.
1. You won't be able to specify `base` parameter meaning that you will be dealing only with default base value which is EUR.
