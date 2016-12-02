(ns solsort.blog-editor.blog-editor
  (:require-macros
   [solsort.toolbox.macros :refer [<? except]]
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
(defn no-repos? [] (= "Not Found" (get (db [:repos-info]) "message")))
(defn current-is-draft? []
  (clojure.string/starts-with? (db [:current :path] "") "_drafts"))
(defn <gh [endpoint]
  (<ajax (str "https://api.github.com/" endpoint
              "?access_token=" (db [:user :auth "token"]))
         :credentials false))
(defn hide-editor! [] (aset js/editorparent.style "height" "0px"))
(defn show-editor! [] (aset js/editorparent.style "height" "auto"))
(defn set-editor-content! [s] (js/CKEDITOR.instances.editor.setData s))
(defn decode-utf8 [s] (js/decodeURIComponent (js/escape s)))
(defn <load-from-github [file]
  (go
    (let [o (clojure.walk/keywordize-keys
             (<? (<gh (str "repos/" (db [:repos]) "/contents/" (:path file)))))
          content (decode-utf8 (js/atob (:content o)))
          [header body] (clojure.string/split content #"\n---\w*\n" 2)
          header (into {} (map #(clojure.string/split % #":\w*" 2)
                               (rest (clojure.string/split header "\n"))))]
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
(defn <list-repos-files [path]
  (go
    (except
     (<? (<gh (str "repos/" (db [:repos]) "/contents/" path)))
     [])))
(defn <update-files []
  (go
    (db!
    [:files]
    (map file-info
         (filter #(re-find #"[.]html$" (get % "path"))
                 (concat
                  (<? (<list-repos-files "_drafts"))
                  (<? (<list-repos-files "_posts"))
                  ))))))
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
                   (<load-from-github file)
                   (db! [:selected-file] file))
      }
     ]
    (for [file (reverse (sort-by :date (db [:files])))]
      (let [v (prn-str file)]
        [:option {:style {:padding-left 0
                          :padding-right 0}
                  :key v :value v} (str (:date file) " \u00a0 " (:title file))]))
    ))
(defn repos-name []
  [:span
   [:code (db [:repos])] " "
   [:button.secondary.small.ui.button {:on-click #(js/location.reload)}
    "Change repository"]])
(defn command:delete []
  (js/alert "not implemented yet"))
(defn command:unpublish []
  (js/alert "not implemented yet"))
(defn command:publish []
  (js/alert "not implemented yet"))
(defn command:save []
  (js/alert "not implemented yet"))
(defn command:new []
  (db! [:current]
       {:body ""
        :path ""
        :sha ""
        :header
        {"date" (.toISOString (js/Date.))
         "layout" "post"
         "title" ""}})
  (db! [:selected-file] nil)
  )
(defn command-bar []
  [:span.ui.basic.buttons
   [:button.small.ui.button
    {:on-click command:new}
    "new"]
   [:button.small.ui.button
    {:on-click command:delete}
    "delete"]
   (if (current-is-draft?)
     [:button.small.ui.button
      {:on-click command:publish}
      "publish"]
     [:button.small.ui.button
      {:on-click command:unpublish}
      "unpublish"])])
(defn ui:filename-save []
  [:div.field
   [:label "Filename"]
   [:div.ui.action.input
    [:input {:style {:text-align :center}
                                  :read-only true
                                  :value (db [:current :path])}]
    [:button.primary.ui.button
     {:on-click command:save}
     "Save"]]
   ]
  )
(defn ui:date-title []
  [:div.fields
   [:div.field [:label "Title"] [input {:db [:current :header "title"]}]]
   [:div.field [:label "Date"] [input {:db [:current :header "date"]}]]
   [:div.field [:label "\u00a0"] [:button.fluid.ui.button "Update filename"]]])
(defn ui:file-settings []
  [:div.ui.form
   [ui:date-title]
   [ui:filename-save]])
(defn ui:app []
  (show-editor!)
  [:div
   [repos-name]
   [:p]
   [file-list] " "
   [command-bar]
   [:p]
   [ui:file-settings]
   ])
(defn ui:about-create []
  (hide-editor!)
  [:div
   [repos-name]
   [:h1 "Repository does not exist."]
   [:p "Make sure you wrote the correct repository name, or choose change repository."]
   [:p "A sample repository name that you can try out is: "
    [:a {:on-click (fn [] (js/localStorage.setItem "blog-editor-repos" "rasmuserik/writings") (js/location.reload))} "rasmuserik/writings"]]]
  )
(defn ui:main []
  [:div
   (if (= -1 (.indexOf js/location.hash "muBackendLoginToken"))
     [welcome]
     (if (no-repos?)
       [ui:about-create]
      [ui:app]))])
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
      (when (not (db [:user :info]))
        (db! [:user :info] (<? (<gh "user"))))
      (when (empty? (db [:repos]))
        (db! [:repos] (str (db [:user :info "login"]) ".github.io")))
      (when (not (re-find #"/" (db [:repos] "")))
        (db! [:repos] (str (db [:user :info "login"]) "/" (db [:repos]))))
      (when (empty? (db [:repos-info]))
        (db! [:repos-info] (<? (<gh (str "repos/" (db [:repos]))))))
      (when
          (and
           (db [:repos-info "id"])
           (not (db [:files])))
        (<? (<update-files))
        (<? (<load-from-github (first (reverse (sort-by :date (db [:files])))))))))
  (render [ui:main]))
