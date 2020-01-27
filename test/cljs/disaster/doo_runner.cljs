(ns disaster.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [disaster.core-test]))

(doo-tests 'disaster.core-test)

