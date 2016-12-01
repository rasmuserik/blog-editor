(ns solsort.blog-editor.blog-editor
  (:require-macros
   [solsort.toolbox.macros :refer [<?]]
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [cljs.reader]
   [solsort.toolbox.setup]
   [solsort.toolbox.appdb :refer [db db! db-async!]]
   [solsort.toolbox.ui :refer [input select]]
   [solsort.util
    :refer
    [<ajax <seq<! js-seq load-style! put!close!
     parse-json-or-nil log page-ready render dom->clj]]
   [reagent.core :as reagent :refer []]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :refer [>! <! chan put! take! timeout close! pipe]]))

(db! [:repos] (js/localStorage.getItem "blog-editor-repos"))
(defn <gh [endpoint]
  (<ajax (str "https://api.github.com/" endpoint "?access_token=" (db [:user :auth "token"]))
                                        ;:headers {:authorization (str "token " )}
         :credentials false
         ))
(defn file-info [o]
  {:draft (not (re-find #"^_posts" (o "path")))
   :sha (get o "sha")
   :date (.slice (o "name") 0 10)
   :file (o "path")
   :title (.replace (.slice (o "name") 11 -5) (js/RegExp "-" "g") " ")
   }
  )
(let [pos (.indexOf js/location.hash "muBackendLoginToken=")]
  (when (not= -1 pos)
    (go
      (when (not (db [:user :auth]))
        (try
          (db! [:user :auth]
               (<? (<ajax (str
                           "https://mubackend.solsort.com/auth/result/"
                           (.slice location.hash (+ pos 20)))
                          :credentials false)))
          (catch js/Error e
            (aset js/location "hash" "")
            (js/location.reload))))
      (when (not (db [:user :info])) (db! [:user :info] (<? (<gh "user"))))
      (when (empty? (db [:repos])) (db! [:repos] (str (db [:user :info "login"]) ".github.io")))
      (when (not (re-find #"/" (db [:repos]))) (db! [:repos] (str (db [:user :info "login"]) "/" (db [:repos]))))
      (when (not (db [:files]))
        (db!
         [:files]
         (map file-info
          (filter #(re-find #"[.]html$" (get % "path"))
                  (concat
                   (<? (<gh (str "repos/" (db [:repos]) "/contents/_drafts")))
                   (<? (<gh (str "repos/" (db [:repos]) "/contents/_posts"))))))))
      (log (db []))
      )))
(defn welcome []
  [:div.ui.container
   [:h1 "Blog Editor"]

   [:p
    "This is a simple wysiwyg editor to edit posts/drafts of "
    [:a {:href "https://pages.github.com"} "GitHub Pages"]]
   [:div.ui.form
    [:div.field
     [:label "Organisation/repository (leave blank for default)"]
     [:input {:type :text
              :placeholder "organisation/repository"
              :on-change (fn [o]
                           (db! [:repos] (.-value (.-target o)))
                           (js/localStorage.setItem "blog-editor-repos" (db [:repos])))
              :value (db [:repos])}]]]
   [:p]
   [:div
    [:a.primary.ui.button {:href
                           (str
                            "https://mubackend.solsort.com/auth/github?url="
                            js/location.origin
                            js/location.pathname)}
     "Login to GitHub"]]])

(defn app []
  [:div.ui.container
   [:p [:code (db [:repos])] " " [:div.secondary.small.ui.button {:on-click #(js/location.reload)} "Change repository"]]
   (into
    [:div]
    (for [file (reverse (sort-by :date (db [:files])))]
      [:div.ui.button
       [:small (:date file) [:br]]
       (:title file)]
      ))
   ])
(defn main []
  (if (= -1 (.indexOf js/location.hash "muBackendLoginToken"))
    [welcome]
    [app]))
(render [main])
