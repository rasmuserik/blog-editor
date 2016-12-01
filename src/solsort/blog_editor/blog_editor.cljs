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

(db! [:repos] (or (js/localStorage.getItem "blog-editor-repos") "rasmuserik/writings"))
(defn <gh [endpoint]
  (<ajax (str "https://api.github.com/" endpoint "?access_token=" (db [:user :auth "token"]))
                                        ;:headers {:authorization (str "token " )}
         :credentials false))

(defn hide-editor! [] (aset js/editorparent.style "height" "0px"))
(defn show-editor! [] (aset js/editorparent.style "height" "auto"))
(defn set-editor-content! [s] (js/CKEDITOR.instances.editor.setData s))

(defn load-from-github [file]
  (go
    (let [o (clojure.walk/keywordize-keys (<? (<gh (str "repos/" (db [:repos]) "/contents/" (:path file)))))
          content (js/atob (:content o))
          [header body] (clojure.string/split content #"\n---\w*\n" 2)
          header (into {} (map #(clojure.string/split % #":\w*") (rest (clojure.string/split header "\n"))))]
      (db! [:current]
           {:header header
            :body body
            :sha (:sha o)
            :path (:path file)})
      (set-editor-content! body))))

(defn file-info [o]
  {:draft (not (re-find #"^_posts" (o "path")))
   :sha (get o "sha")
   :date (.slice (o "name") 0 10)
   :path (o "path")
   :title (.replace (.slice (o "name") 11 -5) (js/RegExp "-" "g") " ")})
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
                       (<? (<gh (str "repos/" (db [:repos]) "/contents/_posts")))))))
        (load-from-github (first (reverse (sort-by :date (db [:files])))))
        ))))


(defn welcome []
  (hide-editor!)
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

(defn file-list []
   (into
    [:select
     {:style {:padding-left 0
              :padding-top 7
              :border-radius 5
              :padding-bottom 7
              :background :white
              :padding-right 0}
      ;:onChange #(db! [:selected-file] (cljs.reader/read-string (.-value (.-target %1))))
      :onChange #(let [file (cljs.reader/read-string (.-value (.-target %1)))]
                   (log 'here file)
                   (load-from-github file)
                   (db! [:selected-file] file))
      }
     ]
    (for [file (reverse (sort-by :date (db [:files])))]
      (let [v (prn-str file)]
        [:option {:style {:padding-left 0
                          :padding-right 0}
                  :key v :value v} (str (:date file) " \u00a0 " (:title file))]))
    ))

(defn app []
  (show-editor!)
  [:div.ui.container
   [:div
    [:div {:style {:float :right :display :inline-block}}[:code (db [:repos])] " " [:div.secondary.small.ui.button {:on-click #(js/location.reload)} "Change repository"]]  
    [file-list] " " [:div.primary.ui.button "new"] ] ])
(defn main []
  [:div
   [:div.ui.red.label "Under development, not functional yet"]
   (if (= -1 (.indexOf js/location.hash "muBackendLoginToken"))
     [welcome]
     [app])])
(render [main])
